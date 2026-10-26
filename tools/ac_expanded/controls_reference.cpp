#include <fstream>
#include <sstream>
#include <iostream>
#include <map>
#include <vector>
#include <cstring>
#include "ir_Tcl.h"
static std::vector<std::string> split(const std::string &s) {
  std::vector<std::string> result;std::stringstream stream(s);std::string part;
  while(std::getline(stream,part,','))result.push_back(part);
  return result;
}
int main(int argc,char**argv) {
  if(argc!=3)return 2;
  std::map<std::string,std::vector<std::string>> sources;
  std::ifstream input(argv[1]);std::string line;
  while(std::getline(input,line)){auto f=split(line);sources[f[0]]=f;}
  std::ifstream vectors(argv[2]);int count=0;
  while(std::getline(vectors,line)){
    auto f=split(line);if(f.size()!=7)return 3;if(!f[5].empty())continue;
    auto source=sources.at(f[0]);uint8_t raw[14],expected[14];
    for(int i=0;i<14;i++){raw[i]=std::stoul(source[1].substr(i*2,2),nullptr,16);expected[i]=std::stoul(f[6].substr(i*2,2),nullptr,16);}
    const uint8_t fans[]={kTcl112AcFanAuto,kTcl112AcFanLow,kTcl112AcFanMed,kTcl112AcFanHigh};
    int swingIndex=std::stoi(f[4]);uint8_t swing=std::stoul(source[2].substr(swingIndex*8+6,2),nullptr,16);
    IRTcl112Ac t(0);t.setRaw(raw);t.setPower(f[2]=="1");t.setMode(f[1]=="1"?kTcl112AcHeat:kTcl112AcCool);t.setTemp(24);
    t.setFan(fans[std::stoi(f[3])]);t.setSwingVertical(swing);
    if(std::memcmp(t.getRaw(),expected,14)||t.getFan()!=fans[std::stoi(f[3])]||t.getSwingVertical()!=swing){std::cerr<<"Public fan/swing mismatch "<<f[0]<<"\n";return 4;}
    count++;
  }
  std::cout<<"Matched "<<count<<" fan/swing packet combinations to the pinned public TCL encoder.\n";
  return count>0?0:5; // Exact count is also checked by the Python caller.
}
