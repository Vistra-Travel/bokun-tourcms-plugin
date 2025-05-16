# ENV

## Bókun
```dotenv
# Required
TOURCMS_ACCOUNT_ID
TOURCMS_CHANNEL_ID

# Send email
SMTP_SERVER
SMTP_USERNAME
SMTP_PASSWORD
# Optional
MAIL_CC

# Optional
TOURCMS_PRIVATE_KEY
TOURCMS_FILTER_IDS
```

## Railway / System env
```dotenv
# Required
TOURCMS_PLUGIN_PORT=443

# TourCMS
# TOURCMS_API_KEY: If it's configured here it will take precedence.
TOURCMS_API_KEY
# Optional
TOURCMS_MARKETPLACE_ID
# Optional
TOURCMS_CHANNEL_ID

# Telegram
# Optional
TELEGRAM_BOT_TOKEN=<default_in_code>
# Optional
TELEGRAM_GROUP_ID=<default_in_code>
# Optional
MAX_RETRIES=3
# Optional
RETRY_DELAY=5000

# Using BókunClient for this project <- Ignore this one because this project doesn't use it
# Optional
BOKUN_API_KEY
# Optional
BOKUN_SECRET

# Webhook
# Optional
WEBHOOK_URL=<default_in_code>
```

# Some cURL for local testing

```
curl -X GET http://localhost:9090/plugin/definition
```

```
curl -X POST http://localhost:9090/product/search \
-H "Content-Type: application/json" \
-d '{
   "parameters": [
     {
       "name": "TOURCMS_ACCOUNT_ID",
       "value": "12345"
     },
     {
       "name": "TOURCMS_CHANNEL_ID",
       "value": "1234"
     },
     {
       "name": "TOURCMS_PRIVATE_KEY",
       "value": "abcxyz123123"
     }
   ]
 }'
```

```
curl -X POST http://localhost:9090/product/getById \
-H "Content-Type: application/json" \
-d '{
   "externalId": "48",
   "parameters": [
     {
       "name": "TOURCMS_ACCOUNT_ID",
       "value": "58193"
     },
     {
       "name": "TOURCMS_CHANNEL_ID",
       "value": "3930"
     },
     {
       "name": "TOURCMS_PRIVATE_KEY",
       "value": "Q3NujxeAumuTvJbWF"
     }
   ]
 }'
```

```
curl -X POST http://localhost:9090/product/getAvailability \
-H "Content-Type: application/json" \
-d '{
   "range": {
     "from": {
       "year": 2025,
       "month": 5,
       "day": 14
     },
     "to": {
       "year": 2025,
       "month": 05,
       "day": 16
     }
   },
   "productId": "48",
   "parameters": [
     {
       "name": "TOURCMS_ACCOUNT_ID",
       "value": "58193"
     },
     {
       "name": "TOURCMS_CHANNEL_ID",
       "value": "3930"
     },
     {
       "name": "TOURCMS_PRIVATE_KEY",
       "value": "Q3NujxeAumuTvJbWF"
     }
   ]
 }'
```

```
curl -X POST https://hook.eu1.make.com/wb7kjoyhpp1nvpa5dkhcqlhrub09h5es \
     -H "Content-Type: application/json" \
     -d '{
          "platform": "BOKUN_TOURCMS_PLUGIN",
          "booking_confirmation_code": "123456789",
          "first_name": "John",
          "last_name": "Doe",
          "voucher_link": "https://example.com/voucher/12345",
          "phone_number": "+84987654321"
     }'
```
