#include <fstream>
#include <sstream>
#include <iostream>
#include <iomanip>
#include <cstring>
#include "ir_Mitsubishi.h"
#include "ir_Tcl.h"
#include "IRsend_test.h"

static std::string hex(const uint8_t* p) {
  std::ostringstream s;for(int i=0;i<14;i++)s<<std::hex<<std::setfill('0')<<std::setw(2)<<(int)p[i];return s.str();
}
int main(int argc,char** argv) {
  if(argc!=2)return 2;
  std::ifstream in(argv[1]);std::string line;int count=0;
  while(std::getline(in,line)) {
    auto comma=line.find(',');std::string model=line.substr(0,comma),initial=line.substr(comma+1);
    uint8_t raw[14];for(int i=0;i<14;i++)raw[i]=std::stoul(initial.substr(i*2,2),nullptr,16);
    for(int heat=0;heat<2;heat++)for(int temp=16;temp<=30;temp++)for(int power=0;power<2;power++) {
      IRMitsubishi112 m(0);m.setRaw(raw);m.setPower(power);m.setMode(heat?kMitsubishi112Heat:kMitsubishi112Cool);m.setTemp(temp);
      IRTcl112Ac t(0);t.setRaw(raw);t.setPower(power);t.setMode(heat?kTcl112AcHeat:kTcl112AcCool);t.setTemp(temp);
      if(hex(m.getRaw())!=hex(t.getRaw())||m.getPower()!=bool(power)||t.getPower()!=bool(power)||m.getTemp()!=temp||t.getTemp()!=temp||m.getMode()!=(heat?1:3)||t.getMode()!=(heat?1:3))return 3;
      std::cout<<model<<","<<heat<<","<<temp<<","<<power<<","<<hex(m.getRaw())<<"\n";count++;
    }
  }
  std::cerr<<"Validated "<<count<<" packets with both pinned public encoders.\n";
}
