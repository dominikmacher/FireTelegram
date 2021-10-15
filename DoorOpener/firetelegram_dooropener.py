import RPi.GPIO as gpio
import requests

url = 'http://10.0.0.60/feuerwehr-karlstetten/firetelegram/?get=alarmstatus'
ioPin = 18;

gpio.setwarnings(False)
gpio.setmode(gpio.BCM)
gpio.setup(ioPin, gpio.OUT)

r = requests.get(url)
data = r.json()
if data["ACTIVE_ALARM"] :
  print("Active!")
  gpio.output(ioPin, gpio.LOW)
else:
  print("Inactive")
  gpio.output(ioPin, gpio.HIGH)
