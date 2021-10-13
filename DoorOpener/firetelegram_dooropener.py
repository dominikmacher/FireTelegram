import RPi.GPIO as gpio
import requests

url = 'http://10.0.0.60/feuerwehr-karlstetten/firetelegram/?get=alarmstatus'

gpio.setwarnings(False)
gpio.setmode(gpio.BCM)
gpio.setup(18, gpio.OUT)

r = requests.get(url)
data = r.json()
if data["ACTIVE_ALARM"] :
  print("Active!")
  gpio.output(18, gpio.HIGH)
else:
  print("Inactive")
  gpio.output(18, gpio.LOW)
