// SPDX-License-Identifier: LGPL-2.1-or-later
// Protocol encoding, state conversions and checksums are provided by the
// unmodified IRremoteESP8266 library; this adapter only records mark/space calls.
#include "ac_engine.h"
#include "IRac.h"
#include "IRutils.h"
#include <climits>
#include <mutex>
#include <stdexcept>

namespace palmremote {
namespace {
std::mutex engineMutex;
Waveform *recording = nullptr;
bool lastWasMark = false;
uint64_t totalDuration = 0;

stdAc::state_t toUpstream(const std::string &protocol, int model, const State &s) {
  if (s.temperature < 16 || s.temperature > 32)
    throw std::invalid_argument("Temperature must be between 16 and 32 C");
  if (s.mode < 0 || s.mode > 4 || s.fan < 0 || s.fan > 5)
    throw std::invalid_argument("Unsupported AC mode or fan speed");
  stdAc::state_t state;
  IRac::initState(&state);
  state.protocol = strToDecodeType(protocol.c_str());
  state.model = model;
  state.power = s.power;
  state.mode = static_cast<stdAc::opmode_t>(s.mode);
  state.degrees = s.temperature;
  state.celsius = true;
  state.fanspeed = static_cast<stdAc::fanspeed_t>(s.fan);
  state.swingv = s.swing ? stdAc::swingv_t::kAuto : stdAc::swingv_t::kOff;
  return state;
}
}

void recordPulse(bool mark, uint32_t duration, uint32_t frequency) {
  if (!recording || !duration) return;
  if (frequency < 20000 || frequency > 60000)
    throw std::runtime_error("AC encoder produced an invalid carrier");
  if (recording->frequency && recording->frequency != static_cast<int>(frequency))
    throw std::runtime_error("This remote changes carrier during a command");
  recording->frequency = frequency;
  if (recording->pattern.empty() && !mark) return;
  if (duration > 1000000 || totalDuration + duration > 1900000)
    throw std::runtime_error("AC command exceeds Android's 1.9 second IR limit");
  totalDuration += duration;
  if (!recording->pattern.empty() && lastWasMark == mark) {
    if (recording->pattern.back() > INT_MAX - static_cast<int>(duration))
      throw std::runtime_error("AC timing overflow");
    recording->pattern.back() += duration;
  } else {
    if (recording->pattern.size() >= 2000)
      throw std::runtime_error("AC command has too many timings");
    recording->pattern.push_back(duration);
  }
  lastWasMark = mark;
}

Waveform encode(const std::string &protocol, int model, const State &desired,
                const State *previous) {
  std::lock_guard<std::mutex> lock(engineMutex);
  auto next = toUpstream(protocol, model, desired);
  // Upstream's YORK dispatcher and power setter are unfinished at this commit.
  // Do not expose a nonfunctional stateful profile; captured York remotes can
  // still be imported via the application's fixed-state catalog.
  if (!IRac::isProtocolSupported(next.protocol) || next.protocol == YORK)
    throw std::invalid_argument("Unsupported air conditioner protocol");
  stdAc::state_t prior;
  if (previous) prior = toUpstream(protocol, model, *previous);
  Waveform output{0, {}};
  recording = &output;
  lastWasMark = false;
  totalDuration = 0;
  try {
    bool ok;
    if (next.protocol == TEKNOPOINT) {
      // Upstream's common dispatcher incorrectly uses TCL timings for this
      // shared state class. Its dedicated encoder provides Teknopoint timings.
      IRTcl112Ac encoder(kGpioUnused);
      encoder.begin();
      encoder.setModel(tcl_ac_remote_model_t::GZ055BE1);
      encoder.setPower(next.power);
      encoder.setMode(IRTcl112Ac::convertMode(next.mode));
      encoder.setTemp(next.degrees);
      encoder.setFan(IRTcl112Ac::convertFan(next.fanspeed));
      encoder.setSwingVertical(IRTcl112Ac::convertSwingV(next.swingv));
      encoder.setSwingHorizontal(false);
      encoder.setQuiet(false);
      encoder.setTurbo(false);
      encoder.setLight(false);
      encoder.setEcono(false);
      encoder.setHealth(false);
      encoder._irsend.sendTeknopoint(encoder.getRaw(), kTcl112AcStateLength);
      ok = true;
    } else {
      IRac encoder(kGpioUnused);
      ok = encoder.sendAc(next, previous ? &prior : nullptr);
    }
    recording = nullptr;
    if (!ok || output.pattern.empty())
      throw std::runtime_error("AC encoder did not produce an IR command");
    return output;
  } catch (...) {
    recording = nullptr;
    throw;
  }
}

std::vector<std::string> supportedProtocols() {
  std::vector<std::string> output;
  for (int i = 1; i <= kLastDecodeType; ++i)
    if (i != YORK && IRac::isProtocolSupported(static_cast<decode_type_t>(i)))
      output.push_back(typeToString(static_cast<decode_type_t>(i)));
  return output;
}
}
