#include <fstream>
#include <sstream>
#include <vector>
#include <iostream>
#include "IRsend_test.h"
#include "IRrecv.h"
int main(int argc,char**argv){
 if(argc!=2)return 2;
 std::ifstream input(argv[1]);std::string line;int cases=0;
 IRrecv recv(0);IRsendTest ir(0);
 while(std::getline(input,line)){
  std::stringstream ss(line);std::string field;std::vector<std::string> fields;
  while(std::getline(ss,field,','))fields.push_back(field);
  int family=std::stoi(fields[0]);std::string expected=fields[1];
  // Capture full 32-bit durations, including the Samsung final idle.
  ir.reset();for(size_t i=3;i<fields.size();i++){uint32_t value=std::stoul(fields[i]);if((i-3)%2==0)ir.mark(value);else ir.space(value);}ir.makeDecodeResult();
  bool ok=false;
  if(family==0)ok=recv.decodeLG(&ir.capture,1,28,true);
  if(family==1)ok=recv.decodeMitsubishiAC(&ir.capture,1,144,true);
  if(family==2)ok=recv.decodeDaikin160(&ir.capture,1,160,true);
  if(family==3)ok=recv.decodeSamsungAC(&ir.capture,1,168,true);
  if(!ok){std::cerr<<"decode failed case "<<cases<<" family "<<family<<" packet "<<expected<<"\n";return 1;}
  if(family==0){uint64_t value=std::stoull(expected.substr(0,6),nullptr,16)*16+std::stoul(expected.substr(6),nullptr,16);if(ir.capture.value!=value){std::cerr<<"LG payload differs\n";return 1;}}
  else for(size_t i=0;i<expected.size()/2;i++)if(ir.capture.state[i]!=std::stoul(expected.substr(i*2,2),nullptr,16)){std::cerr<<"payload differs case "<<cases<<"\n";return 1;}
  cases++;
 }
 std::cout<<"Decoded "<<cases<<" Android-generated waveforms with strict upstream decoders.\n";return cases==960?0:1;
}
