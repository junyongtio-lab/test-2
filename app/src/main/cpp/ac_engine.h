// SPDX-License-Identifier: LGPL-2.1-or-later
#ifndef PALMREMOTE_AC_ENGINE_H
#define PALMREMOTE_AC_ENGINE_H
#include <cstdint>
#include <string>
#include <vector>

namespace palmremote {
struct State {
  bool power;
  int temperature;
  int mode;
  int fan;
  bool swing;
};
struct Waveform {
  int frequency;
  std::vector<int> pattern;
};
Waveform encode(const std::string &protocol, int model, const State &desired,
                const State *previous = nullptr);
std::vector<std::string> supportedProtocols();
}
#endif
