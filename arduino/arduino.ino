const long loggingTime = 15000;
const long multiplier = 60000 / loggingTime;
const int powerPin = 4;
const int interruptPin = 2;
const char separator[] = " - ";

unsigned long counts = 0;
unsigned long cpm = 0;
unsigned long currentMillis = 0;
unsigned long previousMillis = 0;
double averageCounts = 0;
double averageCPM = 0;
double averageMicroSv = 0;
double totalCPM = 0;
double totalMicroSv = 0;
double microSv = 0;
bool enableRun = false;

void tubePulse() {
  counts++;
}

void setup() {
  Serial.begin(9600);
  pinMode(powerPin, OUTPUT);
  attachInterrupt(digitalPinToInterrupt(interruptPin), tubePulse, FALLING);

  Serial.println("--------- SERIAL GEIGER COUNTER v1.2.0 ---------");
  Serial.println("Type 's' to start/stop the program");

  Serial.print("Format: execution_time");
  Serial.print(separator);
  Serial.print("CPM");
  Serial.print(separator);
  Serial.print("average_CPM");
  Serial.print(separator);
  Serial.print("µSv");
  Serial.print(separator);
  Serial.println("average_µSv");
}

void loop() {
  checkCommand();
  counterRead();
}

void checkCommand() {
  if (Serial.available() <= 0) return;

  char cmd = Serial.read();
  if (cmd == 's') {
    digitalWrite(powerPin, !digitalRead(powerPin));
    enableRun = !enableRun;
  }
}

void counterRead() {
  if (!enableRun) return;

  currentMillis = millis();
  if (currentMillis - previousMillis <= loggingTime) return;

  previousMillis = currentMillis;

  averageCounts++;
  cpm = counts * multiplier;
  microSv = cpm / 151.0;

  totalCPM += (double)cpm;
  totalMicroSv += microSv;

  averageCPM = totalCPM / averageCounts;
  averageMicroSv = totalMicroSv / averageCounts;

  Serial.print(currentMillis / 1000);
  Serial.print(separator);
  Serial.print(cpm);
  Serial.print(separator);
  Serial.print(averageCPM);
  Serial.print(separator);
  Serial.print(microSv);
  Serial.print(separator);
  Serial.println(averageMicroSv);

  counts = 0;
}
