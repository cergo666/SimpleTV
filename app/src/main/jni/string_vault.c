#include <jni.h>
#include <stdint.h>

static jbyteArray rejected(JNIEnv *env) {
    jclass error = (*env)->FindClass(env, "java/lang/SecurityException");
    if (error != NULL) (*env)->ThrowNew(env, error, "");
    return NULL;
}

static jbyteArray decode(JNIEnv *env, jclass type, jbyteArray input, jbyteArray signer) {
    (void) type;
    jsize length = (*env)->GetArrayLength(env, input);
    if (length < 9) return rejected(env);

    jbyte mode;
    (*env)->GetByteArrayRegion(env, input, 0, 1, &mode);
    if (mode != 0 && mode != 1) return rejected(env);
    jbyte digest[32] = {0};
    if (mode == 1) {
        if (signer == NULL || (*env)->GetArrayLength(env, signer) != 32) return rejected(env);
        (*env)->GetByteArrayRegion(env, signer, 0, 32, digest);
    }
    // The input is a private Base64 decode buffer. Reuse it as scratch space.
    jbyte *buffer = (*env)->GetByteArrayElements(env, input, NULL);
    if (buffer == NULL) return NULL;
    uint8_t *bytes = (uint8_t *) buffer;
    uint32_t state = (uint32_t) bytes[1] << 24 | (uint32_t) bytes[2] << 16
                   | (uint32_t) bytes[3] << 8 | bytes[4];
    uint32_t marker = 0;
    for (jsize i = 0; i < length - 5; i++) {
        state ^= (uint32_t) (uint8_t) digest[i & 31] << ((i & 3) * 8);
        state ^= state << 13;
        state ^= state >> 17;
        state ^= state << 5;
        uint8_t value = (uint8_t) (bytes[i + 5] ^ state);
        if (i < 4) marker = marker << 8 | value;
        else bytes[i - 4] = value;
    }
    jbyteArray output = NULL;
    if (marker == 0x53545632u) {
        output = (*env)->NewByteArray(env, length - 9);
        if (output != NULL) (*env)->SetByteArrayRegion(env, output, 0, length - 9, buffer);
    }
    (*env)->ReleaseByteArrayElements(env, input, buffer, JNI_ABORT);
    if (marker != 0x53545632u) return rejected(env);
    return output;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void) reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass vault = (*env)->FindClass(env, "io/mediacast/simpletv/protection/StringVault");
    if (vault == NULL) return JNI_ERR;
    const JNINativeMethod methods[] = { {"decode", "([B[B)[B", (void *) decode} };
    if ((*env)->RegisterNatives(env, vault, methods, 1) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
