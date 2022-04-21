import base64
from email.mime.text import MIMEText
from googleapiclient.errors import HttpError

from google.oauth2 import service_account
import googleapiclient.discovery




def create_message(sender, to, subject, message_text):
  """Create a message for an email.

  Args:
    sender: Email address of the sender.
    to: Email address of the receiver.
    subject: The subject of the email message.
    message_text: The text of the email message.

  Returns:
    An object containing a base64url encoded email object.
  """
  message = MIMEText(message_text)
  message['to'] = to
  message['from'] = sender
  message['subject'] = subject
  return dict(raw=base64.urlsafe_b64encode(message.as_bytes()))


def send_message(service, user_id, message):
  """Send an email message.

  Args:
    service: Authorized Gmail API service instance.
    user_id: User's email address. The special value "me"
    can be used to indicate the authenticated user.
    message: Message to be sent.

  Returns:
    Sent Message.
  """
  try:
    message = (service.users().messages().send(userId=user_id, body=message)
               .execute())
    print('Message Id: %s' % message['id'])
    return message
  except HttpError as error:
    print('An error occurred: %s' % error)





SCOPES = ['https://www.googleapis.com/auth/sqlservice.admin']
SERVICE_ACCOUNT_FILE = 'credentials.json'

credentials = service_account.Credentials.from_service_account_file(
        SERVICE_ACCOUNT_FILE, scopes=SCOPES)
delegated_credentials = credentials.with_subject('cheking-system@checksyst.iam.gserviceaccount.com')
servise = googleapiclient.discovery.build('gmail', 'v1', credentials=credentials)

text = create_message('cheking-system@checksyst.iam.gserviceaccount.com', 'surganov.denis@gmail.com', 'test', 'test test test')
send_message(servise, 'surganov.denis@gmail.com', text)
