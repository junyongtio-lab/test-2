#include <fstream>
#include <sstream>
#include <iostream>
#include <vector>
#include "IRsend_test.h"
#include "IRrecv.h"
int main(int argc,char**argv) {
  if(argc!=3)return 2;std::ifstream in(argv[1]);std::string line;int cases=0;
  IRrecv recv(0);IRsendTest sender(0);
  while(std::getline(in,line)) {
    std::stringstream ss(line);std::string field;std::vector<std::string> v;
    while(std::getline(ss,field,','))v.push_back(field);
    if(v.size()!=231)return 3;
    sender.reset();
    for(size_t i=3;i<v.size();i++) {
      uint32_t value=std::stoul(v[i]);if((i-3)%2==0)sender.mark(value);else sender.space(value);
    }
    sender.makeDecodeResult();
    if(!recv.decodeMitsubishi112(&sender.capture,1,112,true)){std::cerr<<"Strict decode failed case "<<cases<<"\n";return 4;}
    if(std::stoi(v[2])!=38000)return 7;
    if(sender.capture.decode_type!=(v[0]=="0"?decode_type_t::MITSUBISHI112:decode_type_t::TCL112AC))return 8;
    for(int i=0;i<14;i++)if(sender.capture.state[i]!=std::stoul(v[1].substr(i*2,2),nullptr,16))return 5;
    cases++;
  }
  std::cout<<"Strictly decoded "<<cases<<" app-generated AC112 waveforms.\n";
  return cases==std::stoi(argv[2])?0:6;
}
