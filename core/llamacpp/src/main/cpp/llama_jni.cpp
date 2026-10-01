// JNI bridge for com.guadian.ondevice.llamacpp.LlamaCpp: load a GGUF model, run one
// prompt with greedy decoding, free it. Strings cross as UTF-8 byte arrays because
// JNI's NewStringUTF uses modified UTF-8, which mangles characters outside the BMP.

#include <jni.h>
#include <android/log.h>

#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {

constexpr const char * TAG = "GuadianLlama";

struct Session {
    llama_model * model;
    llama_context * ctx;
    llama_sampler * sampler;
};

void log_to_android(ggml_log_level level, const char * text, void *) {
    if (level == GGML_LOG_LEVEL_WARN || level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
}

std::once_flag backend_once;

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_guadian_ondevice_llamacpp_LlamaCpp_nativeLoad(JNIEnv * env, jclass, jstring path, jint n_ctx, jint n_threads) {
    std::call_once(backend_once, [] {
        llama_log_set(log_to_android, nullptr);
        llama_backend_init();
    });

    const char * model_path = env->GetStringUTFChars(path, nullptr);
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    llama_model * model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(path, model_path);
    if (model == nullptr) return 0;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = n_ctx;
    ctx_params.n_batch = n_ctx;   // the whole prompt goes in one decode call
    ctx_params.n_threads = n_threads;
    ctx_params.n_threads_batch = n_threads;
    ctx_params.no_perf = true;
    llama_context * ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        llama_model_free(model);
        return 0;
    }

    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    return reinterpret_cast<jlong>(new Session{model, ctx, sampler});
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_guadian_ondevice_llamacpp_LlamaCpp_nativeComplete(JNIEnv * env, jclass, jlong handle, jbyteArray prompt_utf8,
                                                           jint max_tokens) {
    auto * s = reinterpret_cast<Session *>(handle);
    const llama_vocab * vocab = llama_model_get_vocab(s->model);

    std::string prompt(static_cast<size_t>(env->GetArrayLength(prompt_utf8)), '\0');
    env->GetByteArrayRegion(prompt_utf8, 0, static_cast<jsize>(prompt.size()), reinterpret_cast<jbyte *>(prompt.data()));

    // Every call starts fresh: no state carries over from the previous page.
    llama_memory_clear(llama_get_memory(s->ctx), true);
    llama_sampler_reset(s->sampler);

    // Special tokens (<|im_start|> ...) are parsed, as llama-server does for a raw prompt.
    const int n_prompt = -llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), nullptr, 0, true, true);
    if (n_prompt <= 0 || n_prompt + max_tokens > static_cast<int>(llama_n_ctx(s->ctx))) return nullptr;
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(vocab, prompt.c_str(), static_cast<int32_t>(prompt.size()), tokens.data(), n_prompt, true, true) < 0) {
        return nullptr;
    }

    std::string out;
    llama_token token = 0;
    llama_batch batch = llama_batch_get_one(tokens.data(), n_prompt);
    for (int i = 0; i < max_tokens; ++i) {
        if (llama_decode(s->ctx, batch) != 0) return nullptr;
        token = llama_sampler_sample(s->sampler, s->ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        char piece[256];
        const int n = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, false);
        if (n < 0) return nullptr;
        out.append(piece, static_cast<size_t>(n));
        batch = llama_batch_get_one(&token, 1);
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(out.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(out.size()), reinterpret_cast<const jbyte *>(out.data()));
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_guadian_ondevice_llamacpp_LlamaCpp_nativeFree(JNIEnv *, jclass, jlong handle) {
    auto * s = reinterpret_cast<Session *>(handle);
    llama_sampler_free(s->sampler);
    llama_free(s->ctx);
    llama_model_free(s->model);
    delete s;
}
