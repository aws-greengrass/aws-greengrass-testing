import sys
import datetime

message = f"Hello {sys.argv[1]}. Current time is: {str(datetime.datetime.now())}."
message += " Evergreen's dev experience is great!"

# print to stdout
print(message)
