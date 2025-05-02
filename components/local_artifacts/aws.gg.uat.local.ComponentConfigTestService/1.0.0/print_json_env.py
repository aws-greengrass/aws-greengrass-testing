import os
import json

json_str = os.environ['testJsonEnvvar']
json_obj = json.loads(json_str)
if json_obj['leafKey'] == 'default value of /nestedKey/leafKey':
    print('Verified JSON interpolation from script')
else:
    print('Failed to verify JSON envvar. Got:', json_str)
