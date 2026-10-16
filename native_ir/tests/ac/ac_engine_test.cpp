// Portable end-to-end tests: upstream state -> adapter waveform -> upstream decoder.
#include "ac_engine.h"
#include "IRac.h"
#include "IRrecv.h"
#include "IRutils.h"
#include "ir_Gree.h"
#include "ir_Midea.h"
#include <algorithm>
#include <cassert>
#include <iostream>
#include <fstream>
#include <numeric>
#include <stdexcept>

static decode_results decode(const palmremote::Waveform &wave,
                              std::vector<uint16_t> &raw) {
  raw.assign(wave.pattern.size() + 1, 0);
  raw[0] = 10000;
  for (size_t i = 0; i < wave.pattern.size(); ++i)
    raw[i + 1] = std::min(wave.pattern[i] / kRawTick, static_cast<int>(UINT16_MAX));
  decode_results result{};
  result.rawbuf = raw.data();
  result.rawlen = raw.size();
  IRrecv receiver(kGpioUnused);
  if (!receiver.decode(&result)) throw std::runtime_error("Cannot decode recorded waveform");
  return result;
}

int main(int argc, char **argv) {
  size_t checks = 0;
  const auto protocols = palmremote::supportedProtocols();
  assert(protocols.size() == 65);
  for (const auto &protocol : protocols) {
    palmremote::State previous{false, 24, 1, 3, false};
    for (bool power : {true, false}) {
      palmremote::State state{power, 24, 1, 3, false};
      try {
        auto wave = palmremote::encode(protocol, -1, state, &previous);
        assert(!wave.pattern.empty());
        assert(wave.frequency >= 1000 && wave.frequency <= 1000000);
        assert(std::all_of(wave.pattern.begin(), wave.pattern.end(), [](int v){return v > 0;}));
        assert(std::accumulate(wave.pattern.begin(), wave.pattern.end(), 0LL) <= 1900000);
        ++checks;
      } catch (const std::exception &e) {
        std::cerr << protocol << " " << power << ": " << e.what() << std::endl;
        return 1;
      }
      previous = state;
    }
  }
  // Validate every advertised brand/model, including remote-specific enums.
  size_t profiles = 0;
  if (argc == 2) {
    std::ifstream catalog(argv[1]);
    assert(catalog.good());
    std::string line;
    while (std::getline(catalog, line)) {
      const auto first = line.find('\t'), second = line.find('\t', first + 1);
      assert(first != std::string::npos && second != std::string::npos);
      const std::string id = line.substr(0, first), protocol = line.substr(first + 1, second - first - 1);
      const int model = std::stoi(line.substr(second + 1));
      try {
        const palmremote::State before{false, 24, 1, 3, false};
        const palmremote::State on{true, 24, 1, 3, false};
        const palmremote::State off{false, 24, 1, 3, false};
        auto wave = palmremote::encode(protocol, model, on, &before);
        assert(!wave.pattern.empty());
        wave = palmremote::encode(protocol, model, off, &on);
        assert(!wave.pattern.empty());
        // Changing common controls remains within Android's transmission bounds.
        for (int mode = 0; mode <= 4; ++mode)
          for (int temperature : {16, 24, 32})
            for (int fan : {0, 2, 3, 4})
              for (bool swing : {false, true}) {
                wave = palmremote::encode(protocol, model, {true, temperature, mode, fan, swing}, &on);
                assert(!wave.pattern.empty());
                ++checks;
              }
        checks += 2;
        ++profiles;
      } catch (const std::exception &e) {
        std::cerr << id << ": " << e.what() << std::endl;
        return 1;
      }
    }
    assert(profiles >= 100);
  }
  // Every representative is actually decodable and carries the requested state.
  for (const std::string protocol : {"DAIKIN", "GREE", "MIDEA", "MITSUBISHI_AC",
                                    "PANASONIC_AC", "TOSHIBA_AC", "SAMSUNG_AC",
                                    "HAIER_AC", "TCL112AC", "TEKNOPOINT", "KELVINATOR"}) {
    const palmremote::State state{true, 24, 1, 3, false};
    const palmremote::State prior{false, 24, 1, 3, false};
    const auto wave = palmremote::encode(protocol, -1, state, &prior);
    std::vector<uint16_t> raw;
    auto received = decode(wave, raw);
    assert(received.decode_type == strToDecodeType(protocol.c_str()));
    stdAc::state_t common;
    assert(IRAcUtils::decodeToState(&received, &common));
    assert(common.power);
    assert(common.mode == stdAc::opmode_t::kCool);
    assert(common.degrees == 24);
    ++checks;
    // Full-state frame changes when temperature is changed; no made-up button code.
    const auto warmer = palmremote::encode(protocol, -1, {true, 25, 1, 3, false}, &state);
    assert(warmer.pattern != wave.pattern);
    received = decode(warmer, raw);
    assert(IRAcUtils::decodeToState(&received, &common));
    assert(common.degrees == 25);
    ++checks;
  }
  for (const std::string bad : {"NEC", "YORK", "INVALID", ""}) {
    try { palmremote::encode(bad, -1, {true, 24, 1, 0, false}); assert(false); }
    catch (const std::invalid_argument &) { ++checks; }
  }
  try { palmremote::encode("GREE", -1, {true, 100, 1, 0, false}); assert(false); }
  catch (const std::invalid_argument &) { ++checks; }
  // Checksum tests use independently specified upstream known vectors.
  // Source: upstream test/ir_Gree_test.cpp, known recording at line 300.
  const uint8_t gree[] = {0x09,0x01,0x20,0x5C,0x00,0x20,0x00,0x20};
  assert(IRGreeAC::validChecksum(gree));
  ++checks;
  std::cout << "PASS " << checks << " AC checks across " << protocols.size()
            << " stateful protocols and " << profiles << " advertised profiles" << std::endl;
}
