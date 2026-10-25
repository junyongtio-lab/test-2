#include <iostream>
#include <iomanip>
#include <cstring>
#include "ir_LG.h"
#include "ir_Mitsubishi.h"
#include "ir_Daikin.h"
#include "ir_Samsung.h"
void out(int family,int heat,int temp,int fan,int power,const uint8_t *b,int size){
 std::cout<<std::dec<<family<<","<<heat<<","<<temp<<","<<fan<<","<<power<<",";
 for(int i=0;i<size;i++)std::cout<<std::hex<<std::setfill('0')<<std::setw(2)<<(int)b[i];std::cout<<"\n";
}
int main(){
 int lf[]={5,0,2,4},mf[]={0,1,2,5},df[]={10,1,3,5},sf[]={0,2,4,5};
 uint8_t m[]={0x23,0xcb,0x26,1,0,0x20,0x18,0,0x36,0x40,0,0,0,0,0,0,0,0xc3};
 uint8_t d[]={0x11,0xda,0x27,0xf0,0x0d,0,0x0f,0x11,0xda,0x27,0,0xd3,0x31,0xf1,0,0,0x1c,0x0a,8,0x1f};
 uint8_t s[]={2,0x92,0x0f,0,0,0,0xf0,1,0xf2,0xae,0x71,0xa0,0x11,0xf0};
 for(int heat=0;heat<2;heat++)for(int temp=16;temp<=30;temp++)for(int fan=0;fan<4;fan++)for(int power=0;power<2;power++){
  IRLgAc lg(0);lg.setRaw(0x8800156,decode_type_t::LG);lg.setMode(heat?kLgAcHeat:kLgAcCool);lg.setTemp(temp);lg.setFan(lf[fan]);lg.setPower(power);
  uint32_t code=power?lg.getRaw():kLgAcOffCommand;
  uint8_t lb[]={(uint8_t)(code>>20),(uint8_t)(code>>12),(uint8_t)(code>>4),(uint8_t)(code&15)};out(0,heat,temp,fan,power,lb,4);
  IRMitsubishiAC mi(0);mi.setRaw(m);mi.setMode(heat?kMitsubishiAcHeat:kMitsubishiAcCool);mi.setTemp(temp);mi.setFan(mf[fan]);mi.setPower(power);out(1,heat,temp,fan,power,mi.getRaw(),18);
  IRDaikin160 da(0);da.setRaw(d);da.setMode(heat?kDaikinHeat:kDaikinCool);da.setTemp(temp);da.setFan(df[fan]);da.setPower(power);out(2,heat,temp,fan,power,da.getRaw(),20);
  IRSamsungAc sa(0);sa.setRaw(s,14);sa.setMode(heat?kSamsungAcHeat:kSamsungAcCool);sa.setTemp(temp);sa.setFan(sf[fan]);sa.setPower(power);
  uint8_t *raw=sa.getRaw();std::memcpy(raw+14,raw+7,7);uint8_t middle[]={1,0xd2,0x0f,0,0,0,0};std::memcpy(raw+7,middle,7);out(3,heat,temp,fan,power,sa.getRaw(),21);
 }
}
