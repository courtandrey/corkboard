CREATE TABLE email_templates (
  id         bigserial   PRIMARY KEY,
  type       text        NOT NULL,
  version    integer     NOT NULL,
  subject    text        NOT NULL,
  html       text        NOT NULL,
  text_body  text        NOT NULL,
  variables  text[]      NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (type, version)
);

COMMENT ON COLUMN email_templates.variables IS
  'Placeholders the copy declares. Only these are substituted, so an @address in prose survives, and a missing one is an error rather than a literal @token in someone''s inbox.';

CREATE TABLE sent_notifications (
  idempotency_id text        PRIMARY KEY,
  type           text        NOT NULL,
  sent_at        timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE sent_notifications IS
  'One row per notification the provider accepted, written after the send. A redelivery finds the row and stops; a crash between send and insert repeats the message, which beats losing it.';

INSERT INTO email_templates (type, version, subject, variables, text_body, html) VALUES (
  'EMAIL_VERIFICATION',
  1,
  'Confirm your email for Lamppostal',
  ARRAY['user_name', 'verification_link'],
  $text$Hi @user_name,

One click and your account is ready: confirm this address and you can pin notes,
respond to neighbours and keep an eye on your own corner of the board.

@verification_link

Until you do, the board is read-only for you — everything is there to read, but
nothing you do sticks.

The link is good for 48 hours. If it expires, sign in and ask for a new one.

— the board
$text$,
  $html$<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Confirm your email</title>
</head>
<body style="margin:0; padding:0; background:#c89f6e; font-family:'Lucida Grande','Lucida Sans Unicode',Tahoma,Verdana,sans-serif; color:#2b2b2b;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#c89f6e;">
  <tr>
    <td align="center" style="padding:28px 12px;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px; background:#fdfbf2; border:1px solid #e4dfc8; border-radius:5px;">
        <tr>
          <td style="padding:22px 26px 8px; font-size:21px; font-weight:bold; letter-spacing:0.2px; color:#3b5998;">
            lampp<span style="color:#b3352c;">&#9679;</span>stal
          </td>
        </tr>
        <tr>
          <td style="padding:0 26px;">
            <div style="height:1px; background:#e4dfc8; line-height:1px;">&nbsp;</div>
          </td>
        </tr>
        <tr>
          <td style="padding:20px 26px 0; font-size:15px; font-weight:bold;">Hi @user_name,</td>
        </tr>
        <tr>
          <td style="padding:10px 26px 0; font-size:13.5px; line-height:1.55;">
            One click and your account is ready: confirm this address and you can pin notes,
            respond to neighbours and keep an eye on your own corner of the board.
          </td>
        </tr>
        <tr>
          <td align="center" style="padding:22px 26px 6px;">
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
              <tr>
                <td align="center" bgcolor="#3b5998" style="border-radius:5px; border:1px solid #2f477a;">
                  <a href="@verification_link" style="display:inline-block; padding:11px 22px; font-size:14px; font-weight:bold; color:#ffffff; text-decoration:none;">Confirm my email</a>
                </td>
              </tr>
            </table>
          </td>
        </tr>
        <tr>
          <td style="padding:6px 26px 0; font-size:11.5px; line-height:1.5; color:#9a948a; word-break:break-all;">
            Or paste this into your browser:<br>@verification_link
          </td>
        </tr>
        <tr>
          <td style="padding:18px 26px 0;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#fef6a9; border:1px solid #e4dfc8; border-radius:3px;">
              <tr>
                <td style="padding:11px 13px; font-size:12.5px; line-height:1.5;">
                  Until you confirm, the board is read-only for you &mdash; everything is there to read,
                  but nothing you do sticks. The link is good for 48 hours.
                </td>
              </tr>
            </table>
          </td>
        </tr>
        <tr>
          <td style="padding:20px 26px 0;">
            <div style="height:1px; background:#e4dfc8; line-height:1px;">&nbsp;</div>
          </td>
        </tr>
        <tr>
          <td style="padding:12px 26px 24px; font-size:11.5px; line-height:1.5; color:#6b6b6b;">
            If you didn&rsquo;t sign up for Lamppostal, ignore this note and nothing happens.
          </td>
        </tr>
      </table>
    </td>
  </tr>
</table>
</body>
</html>
$html$
);
