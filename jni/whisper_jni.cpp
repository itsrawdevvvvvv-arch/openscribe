// JNI bridge between com.openscribe.app.WhisperEngine and whisper.cpp
#include <jni.h>
#include <string>
#include <cstdint>
#include "whisper.h"

static std::string toStr(JNIEnv *env, jstring s) {
    if (!s) return "";
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string r(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return r;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_openscribe_app_WhisperEngine_init(JNIEnv *env, jclass, jstring modelPath) {
    std::string mp = toStr(env, modelPath);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(mp.c_str(), cparams);
    return (jlong) (intptr_t) ctx;
}

extern "C" JNIEXPORT void JNICALL
Java_com_openscribe_app_WhisperEngine_free(JNIEnv *, jclass, jlong handle) {
    whisper_context *ctx = (whisper_context *) (intptr_t) handle;
    if (ctx) whisper_free(ctx);
}

// Returns one line per segment: "<t0cs>|<t1cs>|<text>"  (times in centiseconds)
extern "C" JNIEXPORT jstring JNICALL
Java_com_openscribe_app_WhisperEngine_transcribe(JNIEnv *env, jclass, jlong handle,
                                                 jfloatArray pcm, jstring lang, jint threads) {
    whisper_context *ctx = (whisper_context *) (intptr_t) handle;
    if (!ctx) return env->NewStringUTF("");

    jsize n = env->GetArrayLength(pcm);
    jfloat *data = env->GetFloatArrayElements(pcm, nullptr);
    std::string lg = toStr(env, lang);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.print_realtime   = false;
    p.print_progress   = false;
    p.print_timestamps = false;
    p.print_special    = false;
    p.translate        = false;
    p.no_context       = true;
    p.single_segment   = false;
    p.n_threads        = threads > 0 ? threads : 4;
    p.language         = lg.empty() ? "auto" : lg.c_str();

    std::string out;
    int rc = whisper_full(ctx, p, data, n);
    env->ReleaseFloatArrayElements(pcm, data, JNI_ABORT);

    if (rc == 0) {
        int ns = whisper_full_n_segments(ctx);
        for (int i = 0; i < ns; i++) {
            long long t0 = whisper_full_get_segment_t0(ctx, i);
            long long t1 = whisper_full_get_segment_t1(ctx, i);
            const char *txt = whisper_full_get_segment_text(ctx, i);
            out += std::to_string(t0);
            out += "|";
            out += std::to_string(t1);
            out += "|";
            out += (txt ? txt : "");
            out += "\n";
        }
    }
    return env->NewStringUTF(out.c_str());
}
