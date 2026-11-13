"""Exercise the running service with synthetic accounts. Creates and cancels test tasks."""
import json
import time
import uuid
import re
import urllib.request
from pathlib import Path
from api_client import Client

checks=[]
def check(name, condition, detail=None):
    checks.append({'name':name,'passed':bool(condition),'detail':detail})
    print(('PASS ' if condition else 'FAIL ')+name,flush=True)
    if not condition:
        raise AssertionError((name,detail))

anonymous=Client()
check('health', anonymous.request('/api/health')[0]==200)
check('anonymous rejected', anonymous.request('/api/analysis/tasks')[0]==401)
check('login CSRF required', anonymous.request('/api/auth/login','POST',{'username':'admin','password':'bad'},csrf=False)[0]==403)
check('wrong password rejected', anonymous.request('/api/auth/login','POST',{'username':'admin','password':'bad'})[0]==401)
first=Client(); first.login('analyst1')
second=Client(); second.login('analyst2')
admin=Client(); admin.login('admin')
check('identity',first.request('/api/auth/me')[1]['username']=='analyst1')
check('analyst cannot administer',first.request('/api/admin/config')[0]==403)
check('shop scope rejected',first.request('/api/overview?scopeId=2')[0]==403)
check('all-shop scope rejected',first.request('/api/overview?scopeId=0')[0]==403)
check('own overview available',first.request('/api/overview?scopeId=1')[0]==200)
check('admin all-shop overview',admin.request('/api/overview?scopeId=0')[0]==200)
check('negative history page rejected',first.request('/api/analysis/tasks?page=-1')[0]==400)
body={'question':'上个月各品类销售额是多少？','scopeId':1,'referenceDate':'2026-11-13'}
key=str(uuid.uuid4())
status,task=first.request('/api/analysis/tasks','POST',body,{'Idempotency-Key':key})
check('create task',status==200,task)
task_id=task['id']
status,duplicate=first.request('/api/analysis/tasks','POST',body,{'Idempotency-Key':key})
check('idempotent creation',status==200 and duplicate['id']==task_id)
changed=dict(body,question='不同的问题')
check('idempotency conflict',first.request('/api/analysis/tasks','POST',changed,{'Idempotency-Key':key})[0]==409)
for suffix,method in [('', 'GET'),('/events','GET'),('/report','GET'),('/cancel','POST'),('/retry','POST')]:
    headers={'Idempotency-Key':str(uuid.uuid4())}
    check('cross-user '+suffix+' rejected',second.request('/api/analysis/tasks/'+task_id+suffix,method,headers=headers)[0]==404)
status,cancelled=first.request('/api/analysis/tasks/'+task_id+'/cancel','POST')
check('cancel is terminal',status==200 and cancelled['status'] in ['CANCELLED','SUCCEEDED','FAILED'])
time.sleep(1)
state=first.request('/api/analysis/tasks/'+task_id)[1]
check('terminal stable',state['status']==cancelled['status'])
check('history persisted',any(row['id']==task_id for row in first.request('/api/analysis/tasks')[1]))
with first.opener.open(first.base+'/api/analysis/tasks/'+task_id+'/events',timeout=10) as response:
    original_events=response.read().decode()
ids=[int(value) for value in re.findall(r'^id:(?: )?(\d+)',original_events,re.M)]
check('SSE events ordered',bool(ids) and ids==sorted(set(ids)))
request=urllib.request.Request(first.base+'/api/analysis/tasks/'+task_id+'/events',headers={'Last-Event-ID':str(ids[0])})
with first.opener.open(request,timeout=10) as response:
    replay=response.read().decode()
replayed=[int(value) for value in re.findall(r'^id:(?: )?(\d+)',replay,re.M)]
check('SSE cursor replay',replayed==ids[1:])

check('metrics versioned',all(row['version']=='1.0' for row in first.request('/api/metrics')[1]))
check('logout',first.request('/api/auth/logout','POST')[0]==200)
check('session revoked',first.request('/api/auth/me')[0]==401)
path=Path(__file__).resolve().parents[1]/'evaluation'/'smoke-results.json'
path.write_text(json.dumps({'checks':checks},ensure_ascii=False,indent=2)+'\n')
print(f'{len(checks)} checks passed',flush=True)
