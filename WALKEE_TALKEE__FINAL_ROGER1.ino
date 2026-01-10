#include<SoftwareSerial.h>
SoftwareSerial gsm(9, 10); //make RX arduino line is pin 2, make TX arduino line is pin 3.

#include<LiquidCrystal.h>
LiquidCrystal lcd( 2,3, 4, 5, 6, 7);
int calltemp = 0;
int pos = 0;
int GP = 0;
int calling = 0;
int temp=0,i=0;
char str[15];
String readString;
#define agps  A0
#define callRX A1  
#define call1 A2
#define call2 A3
#define sos A4
#define relay A5
#define RFRX 12
#define RFTX 11
               // Pulse Sensor purple wire connected to analog pin 0
#define buzz 8                // pin to blink led at each beat




int K=0,J=0;
int  gps_status=0;
float latitude=0; 
float logitude=0;                       
String Speed="";
String gpsString="";
char *test="$GPRMC";

void initModule(String cmd, char *res, int t)
{
  while (1)
  {
    Serial.println(cmd);
    gsm.println(cmd);
    delay(100);
    while (gsm.available() > 0)
    {
      if (gsm.find(res))
      {
        Serial.println(res);
        delay(t);
        return;
      }
      else
      {
        Serial.println("Error");
      }
    }
    delay(t);
  }
}





void setup()
{

pinMode(buzz, OUTPUT);
pinMode(relay, OUTPUT);
pinMode(agps, INPUT_PULLUP);
pinMode(call1, INPUT_PULLUP);
pinMode(call2, INPUT_PULLUP);
pinMode(sos, INPUT_PULLUP);
pinMode(RFRX, INPUT_PULLUP);
pinMode(RFTX, OUTPUT);

gsm.begin(9600);
Serial.begin(9600);

  
lcd.begin(16, 2);
//lcd.setCursor(0, 0);
//lcd.print("  SAFTEY");
//lcd.setCursor(0, 1);
//lcd.print("    SYSTEM");

delay(2000);
lcd.clear();
lcd.print("Initializing");
lcd.setCursor(0, 1);
lcd.print("Please Wait...");
delay(1000);


  

 Serial.println("Initializing....");
 initModule("AT","OK",1000);

lcd.clear();
    lcd.print("Initializing");
    lcd.setCursor(0,1);
    lcd.print("MODULE CONNECTED");
    delay(1000);
    initModule("ATE1","OK",1000);
    initModule("AT+CPIN?","READY",1000);

    lcd.clear();
    lcd.print("Initializing");
    lcd.setCursor(0,1);
    lcd.print("NETWORK FOUND");
    delay(1000);




    initModule("AT+CMGF=1","OK",1000);
    initModule("AT+CNMI=2,2,0,0,0","OK",1000);
  

  lcd.clear();
  lcd.print("Initialized");
  lcd.setCursor(0, 1);
  lcd.print("Successfully");
  delay(1000);
  lcd.clear();

  lcd.clear();
  lcd.print("Waiting For GPS");
  lcd.setCursor(0, 1);
  lcd.print("     Signal    ");
  delay(1000);


if (digitalRead(agps) == 0)
  {
    GP=1;
    get_gps();
    show_coordinate();
    delay(2000);
    lcd.clear();
    lcd.print("GPS is Ready");
    lcd.setCursor(0, 1);
    lcd.print("System Ready");
    delay(2000);

  }

  else
  {  
    GP=0;
    lcd.clear();
    lcd.print("system ready");
    lcd.setCursor(0, 1);
    lcd.print("without GPS");
    delay(2000);
  }


gsm.println("AT+CRSL=100");
//AT+CMIC=<gain>
gsm.println("AT+CMIC=8");  //AT+CLVL=<value>

gsm.println("AT+CLVL=100");
}






void loop()

{

call();
serialEvent(); 

if(digitalRead(sos)==0)
 
{
if (GP==1)
  {
  get_gps();
  show_coordinate();
  }
lcd.clear();
lcd.print("NEED HELP");
lcd.setCursor(0,1);
lcd.print("SOS ACTIVE ");
sos1();
delay(1000);
lcd.print("Sending SMS.... ");
delay(1500);
lcd.clear();
lcd.print("System call..... ");
delay(1500); 
Serial.println("System Ready..");
gsm.println("ATD+916263211405;"); 
delay(15000);                     
gsm.println("ATH"); 

 }


else if(digitalRead(call1)==0)
{
lcd.clear();
lcd.print("CALLING ON......");
lcd.setCursor(0,1);
lcd.print("  ROGER-2");


gsm.println("ATD+919685168488;"); 
delay(1000); 

for (pos = 0; pos <= 6000; pos += 1) 

{ 
delay(500);
if(digitalRead(call1)==0)
{
  //temp=1;
  pos=6000;
}
}
gsm.println(" ATH");
lcd.clear();
lcd.print("CALL DISCONNECT");
lcd.setCursor(0,1);
delay(1500); 
}

 else if((digitalRead(call2)==0))
 
{
lcd.clear();
lcd.print("CALLING ON......");
lcd.setCursor(0,1);
lcd.print(" BASE STATION");
delay(1000);

gsm.println("ATD+916263211405;"); 
delay(1000); 

for (pos = 0; pos <= 6000; pos += 1) 

{ 
delay(500);
if(digitalRead(call2)==0)
{
  //temp=1;
  pos=6000;
}
}
gsm.println("ATH");
lcd.clear();
lcd.print("CALL DISCONNECT");
lcd.setCursor(0,1);
delay(1500); 
}

else if(digitalRead(callRX)==0 && (calltemp)==0)
{
calltemp=1;  
gsm.println("ATA");
lcd.clear();
lcd.print("CALL CONNECTED");
lcd.setCursor(0,1);
delay(1500); 
}



else if(digitalRead(callRX)==0 && (calltemp)==1)
{
calltemp=0;  
gsm.println("ATH");
lcd.clear();
lcd.print("CALL DISCONNECT");
lcd.setCursor(0,1);
delay(1500); 
}

//serialEvent(); 

else if(digitalRead(RFRX)==0)
 
{temp=0;
digitalWrite(relay,1);
lcd.clear();
lcd.print("  RF SIGNAL");
lcd.setCursor(0,1);
lcd.print(" AVAILABLE ");
delay(1000);
}
else if(digitalRead(RFRX)==1)
 
{
digitalWrite(relay,0);
lcd.clear();
lcd.print("RF SIGNAL NOT");
lcd.setCursor(0,1);
lcd.print(" AVAILABLE ");
delay(1000);
if (temp==0)
{
sos2();
temp=1;
}
}


lcd.clear();
lcd.print("SYSTEM ACTIVE");
lcd.setCursor(0,1);
digitalWrite(RFTX,1);
delay(1000);
digitalWrite(RFTX,0);
delay(1000);



}

void gpsEvent()
{
  gpsString="";
  while(1)
  {
   while (Serial.available()>0)            //Serial incoming data from GPS
   {
    char inChar = (char)Serial.read();
     gpsString+= inChar;                    //store incoming data from GPS to temparary string str[]
     i++;
    Serial.print(inChar);
     if (i < 7)                      
     {
      if(gpsString[i-1] != test[i-1])         //check for right string
      {
        i=0;
        gpsString="";
      }
     }
    if(inChar=='\r')
    {
     if(i>65)
     {
       gps_status=1;
       break;
     }
     else
     {
       i=0;
     }
    }
  }
   if(gps_status)
    break;
  }
}

void get_gps()
{
  lcd.clear();
  lcd.print("Getting GPS Data");
  lcd.setCursor(0,1);
  lcd.print("Please Wait.....");
   gps_status=0;
   int x=0;
   while(gps_status==0)
   {
    gpsEvent();
    int str_lenth=i;
    coordinate2dec();
    i=0;x=0;
    str_lenth=0;
   }
}
void show_coordinate()
{
    lcd.clear();
    lcd.print("Lat:");
    lcd.print(latitude);
    lcd.setCursor(0,1);
    lcd.print("Log:");
    lcd.print(logitude);
    Serial.print("Latitude:");
    Serial.println(latitude);
    Serial.print("Longitude:");
    Serial.println(logitude);
    Serial.print("Speed(in knots)=");
    Serial.println(Speed);
    delay(2000);
    lcd.clear();
    lcd.print("Speed(Knots):");
    lcd.setCursor(0,1);
    lcd.print(Speed);
}
void coordinate2dec()
{
  String lat_degree="";
    for(i=19;i<=20;i++)         
      lat_degree+=gpsString[i];
      
  String lat_minut="";
     for(i=21;i<=27;i++)         
      lat_minut+=gpsString[i];
  String log_degree="";
    for(i=32;i<=34;i++)
      log_degree+=gpsString[i];
  String log_minut="";
    for(i=35;i<=42;i++)
      log_minut+=gpsString[i];
    
    Speed="";
    for(i=45;i<48;i++)          //extract longitude from string
      Speed+=gpsString[i];
      
     float minut= lat_minut.toFloat();
     minut=minut/60;
     float degree=lat_degree.toFloat();
     latitude=degree+minut;
     
     minut= log_minut.toFloat();
     minut=minut/60;
     degree=log_degree.toFloat();
     logitude=degree+minut;
}

void sms1()
{
  gsm.println("AT");
  delay(500);
  serialPrint();
  gsm.println("AT+CMGF=1");
  delay(500);
  serialPrint();
  gsm.print("AT+CMGS=");
  gsm.print('"');
  gsm.print("6263211405");    //mobile no. for SMS alert
  gsm.println('"');
  delay(500);
  serialPrint();

 gsm.print("ROGER-1 CURRENT LOCATION:,");
 gsm.print("Latitude:");
  //gsm.print("SIRT CAMPUS ");
 gsm.println(latitude);
 delay(500);
 serialPrint();
 //gsm.print("Auditorium-1");
  gsm.print("logitude:");
  gsm.println(logitude);
  delay(500);
  serialPrint();
  
  gsm.print("http://maps.google.com/maps?&z=15&mrt=yp&t=k&q=");
  gsm.print(latitude, 8);
  gsm.print("+");              //28.612953, 77.231545   //28.612953,77.2293563
  gsm.print(logitude, 8);
  gsm.write(26);
  delay(2000);
  serialPrint();
}

void sos1()
{
  gsm.println("AT");
  delay(500);
  serialPrint();
  gsm.println("AT+CMGF=1");
  delay(500);
  serialPrint();
  gsm.print("AT+CMGS=");
  gsm.print('"');
  gsm.print("6263211405");    //mobile no. for SMS alert
  gsm.println('"');
  delay(500);
  serialPrint();

 gsm.print("SOS! ROGER-1 need help.,");
 gsm.print("Lat:");
  //gsm.print("SIRT CAMPUS ");
 gsm.println(latitude);
 delay(500);
 serialPrint();
 //gsm.print("Auditorium-1");
  gsm.print("Lon:");
  gsm.println(logitude);
  delay(500);
  serialPrint();
  
  gsm.print("http://maps.google.com/maps?&z=15&mrt=yp&t=k&q=");
  gsm.print(latitude, 8);
  gsm.print("+");              //28.612953, 77.231545   //28.612953,77.2293563
  gsm.print(logitude, 8);
  gsm.write(26);
  delay(2000);
  serialPrint();
}


void sos2()
{
  gsm.println("AT");
  delay(500);
  serialPrint();
  gsm.println("AT+CMGF=1");
  delay(500);
  serialPrint();
  gsm.print("AT+CMGS=");
  gsm.print('"');
  gsm.print("6263211405");    //mobile no. for SMS alert
  gsm.println('"');
  delay(500);
  serialPrint();

 gsm.print("ROGER-1 ,RADIO SIGNAL LOST LAST LOCATION IS,");
 gsm.print("Lat:");
  //gsm.print("SIRT CAMPUS ");
 gsm.println(latitude);
 delay(500);
 serialPrint();
 //gsm.print("Auditorium-1");
  gsm.print("Lon:");
  gsm.println(logitude);
  delay(500);
  serialPrint();
  
  gsm.print("http://maps.google.com/maps?&z=15&mrt=yp&t=k&q=");
  gsm.print(latitude, 8);
  gsm.print("+");              //28.612953, 77.231545   //28.612953,77.2293563
  gsm.print(logitude, 8);
  gsm.write(26);
  delay(2000);
  serialPrint();
}


void serialEvent() 

 {
  while(gsm.available()) 
  {
    
    if(gsm.find("#LOCATION"))
    {
      digitalWrite(buzz, HIGH);
      delay(1000);
      digitalWrite(buzz, LOW);

      TRI();
    //  temp=1;
      gsm.print("");
    }
   }
 }


void call() 

 {
  while(gsm.available()) 
  {
    
    if(gsm.find("RING"))
    {
      digitalWrite(buzz, HIGH);
      delay(1000);
      digitalWrite(buzz, LOW);
      TRI2();
      gsm.print("");
    }
    else
    {
      calling=0;
      }
      
   }
 }



void TRI()
{
lcd.clear();
lcd.print("SEND LOCATION");
lcd.setCursor(0,1);
lcd.print("FOR BASE STA");
delay(1500);
if (GP==1)
  {
  get_gps();
  show_coordinate();
  }  
lcd.clear();
lcd.print("Sending SMS ");
sms1();
delay(1000);
lcd.clear();
lcd.print("Sending SMS.... ");
delay(1000);
lcd.clear();
lcd.print("System Ready");
    
      
} 

void TRI2()
{
lcd.clear();
lcd.print("call recieving");
lcd.setCursor(0,1);
lcd.print("calling.......");


for (pos = 0; pos <= 30; pos += 1) 

{ 
digitalWrite(buzz,1); 
delay(250);
digitalWrite(buzz,0);
delay(250);

if(digitalRead(callRX)==0)
{
pos=30;
gsm.println("ATA");
lcd.clear();
lcd.print("CALL CONNECTED");
lcd.setCursor(0,1);
delay(1500); 
}
else if(digitalRead(call1)==0)
{
pos=30; 
gsm.println("ATH");
lcd.clear();
lcd.print("CALL DISCONNECT");
lcd.setCursor(0,1);
delay(1500); 
}
}
}







void serialPrint()
{
  while (gsm.available() > 0)
  {
    Serial.print(gsm.read());
  }
}
