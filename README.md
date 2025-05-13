# cURL

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
   "externalId": "134",
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
curl -X POST http://localhost:9090/product/getAvailability \
-H "Content-Type: application/json" \
-d '{
   "range": {
     "from": {
       "year": 2025,
       "month": 5,
       "day": 13
     },
     "to": {
       "year": 2025,
       "month": 6,
       "day": 1
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
