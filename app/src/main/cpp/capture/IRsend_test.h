// PalmRemote hardware-free waveform adapter. SPDX-License-Identifier: LGPL-2.1-or-later
// Compatible with IRremoteESP8266's UNIT_TEST send interface; no GPIO is used.
#ifndef PALMREMOTE_IRSEND_CAPTURE_H
#define PALMREMOTE_IRSEND_CAPTURE_H
#include "IRsend.h"
#include "IRrecv.h"
#include "IRtimer.h"

namespace palmremote {
void recordPulse(bool mark, uint32_t duration, uint32_t frequency);
}

class IRsendTest : public IRsend {
 public:
  decode_results capture{};
  explicit IRsendTest(uint16_t pin, bool inverted = false, bool modulation = true)
      : IRsend(pin, inverted, modulation) {}
  uint16_t mark(uint16_t duration) override {
    IRtimer::add(duration);
    palmremote::recordPulse(true, duration, _freq_unittest);
    return 0;
  }
  void space(uint32_t duration) override {
    IRtimer::add(duration);
    palmremote::recordPulse(false, duration, _freq_unittest);
  }
  // IRac's optional test receiver is never installed by the Android bridge.
  void makeDecodeResult(uint16_t = 0) {}
};
#endif
