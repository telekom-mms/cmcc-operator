#!/bin/bash
echo fixing $1 $2 $3
kubectl -n $1 patch $2 $3 --type=json --patch  '[{"op":"replace", "path":"/metadata/ownerReferences/0/apiVersion", "value": "cmcc.tsystemsmms.com/v2"}]'
