// SPDX-License-Identifier: LGPL-2.1-or-later
#include <jni.h>
#include "ac_engine.h"
#include <exception>

extern "C" JNIEXPORT jintArray JNICALL
Java_com_universalremote_app_ac_AcEngine_nativeEncode(
    JNIEnv *env, jclass, jstring protocol, jint model, jintArray desired,
    jintArray previous) {
  if (!protocol || !desired || env->GetArrayLength(desired) != 5 ||
      (previous && env->GetArrayLength(previous) != 5)) {
    env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                  "Invalid air conditioner state");
    return nullptr;
  }
  const char *name = env->GetStringUTFChars(protocol, nullptr);
  if (!name) return nullptr;
  std::string protocolName(name);
  env->ReleaseStringUTFChars(protocol, name);
  jint d[5], p[5];
  env->GetIntArrayRegion(desired, 0, 5, d);
  palmremote::State state{d[0] != 0, d[1], d[2], d[3], d[4] != 0};
  palmremote::State prior{};
  if (previous) {
    env->GetIntArrayRegion(previous, 0, 5, p);
    prior = {p[0] != 0, p[1], p[2], p[3], p[4] != 0};
  }
  try {
    const auto waveform = palmremote::encode(protocolName, model, state,
                                            previous ? &prior : nullptr);
    std::vector<jint> values;
    values.reserve(waveform.pattern.size() + 1);
    values.push_back(waveform.frequency);
    values.insert(values.end(), waveform.pattern.begin(), waveform.pattern.end());
    jintArray result = env->NewIntArray(values.size());
    if (result) env->SetIntArrayRegion(result, 0, values.size(), values.data());
    return result;
  } catch (const std::exception &error) {
    env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), error.what());
    return nullptr;
  }
}
