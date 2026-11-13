"""Run fixed cases against the Agent and a single-call SQL baseline, recording real usage."""
import argparse
from decimal import Decimal
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.request
import uuid
from api_client import Client

ROOT=Path(__file__).resolve().parents[2]
INPUT_RATE=Decimal('0.0000024')
OUTPUT_RATE=Decimal('0.0000096')

def literal(value):
    return "'"+str(value).replace('\\','\\\\').replace("'","''")+"'"

def sql(query, scope=None):
    suffix='all' if scope==0 else str(scope)
    user='ci_app' if scope is None else 'ci_read_'+suffix
    schema='commerce_app' if scope is None else 'commerce_scope_'+suffix
    environment=os.environ.copy()
    environment['MYSQL_PWD']=os.environ.get('CI_DB_PASSWORD','ci_app_local_demo') if scope is None else os.environ.get('CI_READ_PASSWORD','ci_read_local_demo')
    result=subprocess.run(['mysql','--default-character-set=utf8mb4','--protocol=TCP','-h',os.environ.get('CI_DB_HOST','127.0.0.1'),'-P',os.environ.get('CI_DB_PORT','3308'),'-u',user,'--batch','--raw',schema],
        input='SET SESSION max_execution_time=30000;\n'+query+';\n',text=True,capture_output=True,env=environment,check=True)
    lines=result.stdout.splitlines()
    if not lines:
        return []
    columns=lines[0].split('\t')
    return [dict(zip(columns,[None if value=='NULL' else value for value in row.split('\t')])) for row in lines[1:]]

def canonical(rows, columns):
    def value(cell, column):
        if cell is None:
            return None
        try:
            precision = Decimal('0.01') if any(name in column for name in ['sales', 'amount', 'average_order']) and not column.endswith('_rate') else Decimal('0.000001')
            return Decimal(str(cell)).quantize(precision)
        except Exception:
            return str(cell)
    return sorted([tuple(value(row.get(column), column) for column in columns) for row in rows],key=str)

def matches(expected, actual):
    if not expected:
        return not actual
    columns=list(expected[0])
    return all(all(column in row for column in columns) for row in actual) and canonical(expected,columns)==canonical(actual,columns)

def baseline(case, key, context):
    prompt='''你是电商 SQL 分析师。根据以下授权 Schema、口径和用户问题，一次生成 MySQL 只读查询，只返回 JSON {"sql":"SELECT ..."}。保持用户指定的结果列名，退款视图已经成功过滤和去重。避免一对多退款 JOIN 放大销售额。参考日期 2026-11-13。'''
    user=context+'\n问题='+case['question']
    reserve=(Decimal(len((prompt+user).encode())+1024)*INPUT_RATE+4096*OUTPUT_RATE).quantize(Decimal('0.000001'))
    call_id=str(uuid.uuid4())
    # Atomic budget reservation and call insertion share one MySQL transaction.
    statements=f'''START TRANSACTION;
UPDATE ci_budget SET committed_cny=committed_cny+{reserve} WHERE id=1 AND committed_cny+{reserve}<=limit_cny;
SET @allowed=ROW_COUNT();
INSERT INTO ci_model_calls(task_id,purpose,model,reserved_cny,status) SELECT {literal(call_id)},'baseline','deepseek-flash',{reserve},'RESERVED' WHERE @allowed=1;
COMMIT;
SELECT id FROM ci_model_calls WHERE task_id={literal(call_id)}'''
    reservation=sql(statements)
    if not reservation:
        raise RuntimeError('Project budget exhausted')
    row_id=int(reservation[-1]['id'])
    body={'model':'deepseek-flash','messages':[{'role':'system','content':prompt},{'role':'user','content':user}],
        'temperature':0,'max_tokens':4096,'thinking':{'type':'disabled'}}
    request=urllib.request.Request('https://api.deepseek.com/chat/completions',data=json.dumps(body).encode(),
        headers={'Content-Type':'application/json','Authorization':'Bearer '+key})
    started=time.monotonic()
    try:
        with urllib.request.urlopen(request,timeout=120) as response:
            answer=json.load(response)
        usage=answer['usage']
        charged=Decimal(usage['prompt_tokens'])*INPUT_RATE+Decimal(usage['completion_tokens'])*OUTPUT_RATE
        sql(f"START TRANSACTION; UPDATE ci_model_calls SET charged_cny={charged},input_tokens={usage['prompt_tokens']},output_tokens={usage['completion_tokens']},status='COMPLETED' WHERE id={row_id}; UPDATE ci_budget SET committed_cny=committed_cny+({charged}-{reserve}) WHERE id=1; COMMIT")
        text=answer['choices'][0]['message']['content'].strip()
        if text.startswith('```'):
            text=text.split('\n',1)[1].rsplit('```',1)[0]
        generated=json.loads(text)['sql']
        # The separate baseline DB account only has SELECT on its scope's views.
        rows=sql(generated,case['scope'])
        return {'sql':generated,'rows':rows,'usage':usage,'costCnyConservative':str(charged),'elapsedSeconds':round(time.monotonic()-started,3)}
    except Exception:
        sql(f"UPDATE ci_model_calls SET status='UNKNOWN' WHERE id={row_id} AND status='RESERVED'")
        raise

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--limit',type=int,default=20)
    parser.add_argument('--baseline',action='store_true')
    parser.add_argument('--output',default='commerce/evaluation/results.json')
    args=parser.parse_args()
    cases=json.loads((ROOT/'commerce/evaluation/cases.json').read_text())[:args.limit]
    client=Client(); client.login('admin')
    metrics=client.request('/api/metrics')[1]
    columns=sql("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE FROM information_schema.columns WHERE TABLE_SCHEMA='commerce_scope_1' ORDER BY TABLE_NAME,ORDINAL_POSITION",1)
    context=json.dumps({'schema':columns,'metrics':metrics,'authorizedShops':sql('SELECT id,name FROM shops ORDER BY id',1),'scopeRule':'视图已完成店铺过滤，Schema技术标识不可作为店铺名称'},ensure_ascii=False)
    output={'referenceDate':'2026-11-13','model':'deepseek-flash','pricing':'peak/cache-miss, conservative 8 CNY/USD','cases':[]}
    for case in cases:
        result={'id':case['id'],'question':case['question'],'goldenSql':case['goldenSql']}
        expected=sql(case['goldenSql'],case['scope'])
        result['expected']=expected
        started=time.monotonic()
        status,task=client.request('/api/analysis/tasks','POST',{'question':case['question'],'scopeId':case['scope'],'referenceDate':case['referenceDate']},{'Idempotency-Key':str(uuid.uuid4())})
        if status!=200:
            raise RuntimeError(('Task creation',status,task))
        task_id=task['id']
        while time.monotonic()-started<200:
            status,task=client.request('/api/analysis/tasks/'+task_id)
            if status!=200:
                raise RuntimeError(('Task read',status,task))
            if task['status'] not in ['RUNNING','QUEUED']:
                break
            time.sleep(.6)
        result['agent']={'taskId':task_id,'status':task['status'],'error':task.get('error_message'),
            'elapsedSeconds':round(time.monotonic()-started,3),'calls':task.get('calls',[]),'evidence':task.get('evidence',[]),
            'plan':json.loads(task['plan_json']) if task.get('plan_json') else None,
            'report':json.loads(task['report_json']) if task.get('report_json') else None}
        tables=[json.loads(item['result_json'])['rows'] for item in task.get('evidence',[])]
        result['agent']['matchesGolden']=task['status']=='SUCCEEDED' and any(matches(expected,table) for table in tables)
        if args.baseline:
            try:
                result['baseline']=baseline(case,os.environ.get('CI_MODEL_KEY') or os.environ['ANTHROPIC_AUTH_TOKEN'],context)
                result['baseline']['matchesGolden']=matches(expected,result['baseline']['rows'])
            except Exception as error:
                result['baseline']={'matchesGolden':False,'error':type(error).__name__+': '+str(error)[:200]}
        output['cases'].append(result)
        output['summary']={'count':len(output['cases']),'agentCorrect':sum(item['agent']['matchesGolden'] for item in output['cases']),
            'baselineCorrect':sum(item.get('baseline',{}).get('matchesGolden',False) for item in output['cases']),
            'budget':sql('SELECT committed_cny,limit_cny FROM ci_budget WHERE id=1')[0]}
        (ROOT/args.output).write_text(json.dumps(output,ensure_ascii=False,indent=2,default=str)+'\n')
        print(json.dumps({'id':case['id'],'agentStatus':task['status'],'agentCorrect':result['agent']['matchesGolden'],
            'baselineCorrect':result.get('baseline',{}).get('matchesGolden'),'summary':output['summary']},ensure_ascii=False),flush=True)

if __name__=='__main__':
    main()
