#include <jni.h>
#include <string>
#include <vector>
#include "gguf_context.h"

extern "C" {

static GgufContext* getContext(JNIEnv* env, jobject thiz) {
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "nativePtr", "J");
    return reinterpret_cast<GgufContext*>(env->GetLongField(thiz, field));
}

static void setContext(JNIEnv* env, jobject thiz, GgufContext* ctx) {
    jclass clazz = env->GetObjectClass(thiz);
    jfieldID field = env->GetFieldID(clazz, "nativePtr", "J");
    env->SetLongField(thiz, field, reinterpret_cast<jlong>(ctx));
}

JNIEXPORT jboolean JNICALL
Java_com_kai_custom_inference_GgufNative_nativeInit(
    JNIEnv* env, jobject thiz,
    jstring modelPath,
    jint nCtx
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    auto* ctx = new GgufContext();
    bool ok = ctx->loadModel(path, nCtx);
    env->ReleaseStringUTFChars(modelPath, path);

    if (ok) {
        setContext(env, thiz, ctx);
        return JNI_TRUE;
    }
    delete ctx;
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_kai_custom_inference_GgufNative_nativeChat(
    JNIEnv* env, jobject thiz,
    jstring systemPrompt,
    jobjectArray messagesJson,
    jint topK,
    jfloat topP,
    jfloat temperature,
    jint maxTokens
) {
    GgufContext* ctx = getContext(env, thiz);
    if (!ctx) return env->NewStringUTF("");

    std::vector<GgufChatMessage> messages;
    if (messagesJson != nullptr) {
        jsize len = env->GetArrayLength(messagesJson);
        for (jsize i = 0; i < len; i++) {
            jstring msgStr = (jstring)env->GetObjectArrayElement(messagesJson, i);
            if (msgStr) {
                const char* msg = env->GetStringUTFChars(msgStr, nullptr);
                // Format: "role|||content"
                std::string s(msg);
                auto sep = s.find("|||");
                if (sep != std::string::npos) {
                    messages.push_back({s.substr(0, sep), s.substr(sep + 3)});
                }
                env->ReleaseStringUTFChars(msgStr, msg);
                env->DeleteLocalRef(msgStr);
            }
        }
    }

    const char* sys = systemPrompt ? env->GetStringUTFChars(systemPrompt, nullptr) : "";
    std::string result = ctx->chat(sys, messages, topK, topP, temperature, maxTokens);
    if (systemPrompt) env->ReleaseStringUTFChars(systemPrompt, sys);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT void JNICALL
Java_com_kai_custom_inference_GgufNative_nativeRelease(
    JNIEnv* env, jobject thiz
) {
    GgufContext* ctx = getContext(env, thiz);
    if (ctx) {
        ctx->release();
        delete ctx;
        setContext(env, thiz, nullptr);
    }
}

} // extern "C"
