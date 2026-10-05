#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""独立可丢弃MySQL环境验收；不向真实客户或已部署业务实例写入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import datetime as dt
import http.cookiejar
import json
import os
import secrets
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

base=os.environ.get('BASE_URL','')
if os.environ.get('ALLOW_TEST_DATA')!='1' or urllib.parse.urlparse(base).hostname not in {'127.0.0.1','localhost'}:
    raise SystemExit('Set ALLOW_TEST_DATA=1 and an isolated loopback BASE_URL.')
password=os.environ.get('ADMIN_PASSWORD','')
if not password: raise SystemExit('ADMIN_PASSWORD required through environment.')
checks=0

def verify(condition,label):
    global checks
    checks+=1
    if not condition: raise AssertionError(label)

class Client:
    """带独立Cookie及CSRF的测试账号；不打印凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
    def __init__(self,user=None,pw=None):
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=self.call('/auth/csrf')
        if user: self.call('/auth/login','POST',{'username':user,'password':pw})
    def call(self,path,method='GET',body=None,status=200,csrf=True):
        headers={'Content-Type':'application/json'}
        if method!='GET' and csrf: headers[self.csrf['header']]=self.csrf['token']
        req=urllib.request.Request(base+'/api'+path,data=None if body is None else json.dumps(body).encode(),headers=headers,method=method)
        try:
            with self.opener.open(req,timeout=30) as r: raw=r.read();actual=r.status
        except urllib.error.HTTPError as e: actual=e.code;raw=e.read()
        value=json.loads(raw) if raw else {}
        verify(actual==status,f'{method} {path}: expected {status}, received {actual}, code={value.get("code","") if isinstance(value,dict) else ""}')
        return value


from concurrent.futures import ThreadPoolExecutor

def key():return str(uuid.uuid4())
def today():return dt.datetime.now(dt.timezone.utc).date()
def day(n):return (today()+dt.timedelta(days=n)).isoformat()
admin=Client('admin',password);anonymous=Client()
with urllib.request.urlopen(base+'/actuator/health') as r:verify(json.load(r)['status']=='UP','health')
anonymous.call('/projects',status=401);admin.call('/projects','POST',{},status=403,csrf=False)
suffix=secrets.token_hex(3);testpw='TESTAa9'+secrets.token_hex(16)
roles=admin.call('/admin/roles');role={r['name'].split(' / ')[-1]:r['id'] for r in roles}
accounts={};clients={}
for label,rolename in [('manager','Project manager'),('worker','Site worker'),('finance','Finance'),('client','Client'),('other','Client'),('reviewer','Project manager')]:
 username='test.'+label+'.'+suffix
 a=admin.call('/admin/users','POST',dict(username=username,displayName='TEST '+{'manager':'项目经理','worker':'现场人员','finance':'财务','client':'客户','other':'其他客户','reviewer':'工程复核'}[label],password=testpw,roleId=role[rolename],departmentId=1,enabled=True))
 accounts[label]=dict(username=username,id=a['id']);clients[label]=Client(username,testpw)
manager=clients['manager'];worker=clients['worker'];fin=clients['finance'];reviewer=clients['reviewer']
for s in admin.call('/admin/settings'):
 if s['code']=='companyName':admin.call('/admin/settings/'+str(s['id']),'PUT',dict(value='TEST 工程运营 / Project operations'))

def new(name='TEST 办公室机电安装',code=None):
 return manager.call('/projects','POST',dict(code=code or 'TEST-'+key()[:8],name=name,customer='TEST 项目客户',site='TEST 合成施工地址',departmentId=1,managerId=accounts['manager']['id'],clientId=accounts['client']['id'],startDate=day(-7),endDate=day(30)))
def detail(p,c=admin):return c.call('/projects/'+str(p['id']))
def act(p,path,c=manager,status=200,**fields):
 return c.call('/projects/'+str(p['id'])+path,'POST',dict(revision=detail(p)['project']['revision'],**fields),status)
def work(p,code,name,q='10',price='100',sub='20',budget='300'):
 return act(p,'/works',code=code,name=name,unit='组',quantity=q,price=price,subPrice=sub,vendor='TEST 专业施工班组' if float(sub)>0 else '',costBudget=budget,dueDate=day(20))
def claim(p,w,quantity):return act(p,'/claims',c=worker,workId=w['id'],quantity=quantity,note='TEST 实际验收用合成记录',workDate=day(0))
def review(p,type,row,c=reviewer,status=200,approve=True):return act(p,'/'+type+'/'+str(row['id'])+'/review',c=c,status=status,approve=approve,reviewNote='TEST 核对工程量与凭据')
def bill(p,w,kind,quantity,hold='5'):
 return act(p,'/bills',workId=w['id'],kind=kind,quantity=quantity,retentionPercent=hold,dueDate=day(15),reference='TEST '+kind+'结算凭据')
def pay(p,b,kind,amount,original=None,request=None,status=200):
 fields=dict(kind=kind,amount=amount,reference='TEST 线下交易凭据',note='TEST 已完成线下交易登记',requestKey=request or key())
 if original:fields['originalId']=original['id']
 return act(p,'/bills/'+str(b['id'])+'/money',c=fin,status=status,**fields)

p=new();w=work(p,'W01','TEST 配电与照明安装');act(p,'/members',accountId=accounts['worker']['id']);act(p,'/state/activate')
worker.call('/reports',status=403);clients['client'].call('/admin/users',status=403);clients['other'].call('/projects/'+str(p['id']),status=403)
verify(clients['other'].call('/projects')==[],'other client sees no projects')
site_detail=detail(p,worker);client_detail=detail(p,clients['client'])
verify('summary' not in site_detail and 'money' not in site_detail and 'price' not in site_detail['works'][0],'site JSON has no prices or financial summary')
verify('costs' not in client_detail and 'subPrice' not in client_detail['works'][0],'client JSON has no internal costs or subcontract rates')
verify('actualCost' not in worker.call('/projects')[0] and 'budget' not in clients['client'].call('/projects')[0],'list JSON role privacy')
act(p,'/claims',c=clients['client'],status=403,workId=w['id'],quantity='1',note='TEST',workDate=day(0))
act(p,'/works',status=409,code='BLOCKED')
manager.call('/projects/'+str(p['id'])+'/state/close','POST',{'revision':0},409)
variation=act(p,'/variations',workId=w['id'],quantityDelta='2',budgetDelta='50',reference='TEST 客户确认编号',reason='TEST 增加安装工程量')
verify(detail(p)['works'][0]['quantity']==10,'pending variation does not change scope')
review(p,'variations',variation,c=manager,status=409);review(p,'variations',variation)
verify(detail(p)['works'][0]['quantity']==12,'approved variation changes scope')
c=claim(p,w,'12');act(p,'/claims',c=worker,status=409,workId=w['id'],quantity='1',note='TEST 超量',workDate=day(0))
# Upload actual image bytes as TEST validation material, not a site photo or customer case.
image=(Path(__file__).resolve().parents[1]/'frontend/public/brand/logo.jpg').read_bytes();boundary='----TEST'+key()
data=(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="TEST-image-validation.jpg"\r\nContent-Type: image/jpeg\r\n\r\n').encode()+image+f'\r\n--{boundary}--\r\n'.encode()
req=urllib.request.Request(base+'/api/projects/'+str(p['id'])+'/claims/'+str(c['id'])+'/attachments',data=data,method='POST',headers={'Content-Type':'multipart/form-data; boundary='+boundary,worker.csrf['header']:worker.csrf['token']})
with worker.opener.open(req) as response:attachment=json.load(response);verify(response.status==200,'image accepted')
with worker.opener.open(base+'/api/attachments/'+str(attachment['id'])) as response:verify(response.read()==image,'image persisted and identical')
clients['other'].call('/attachments/'+str(attachment['id']),status=403)
review(p,'claims',c)
cost=act(p,'/costs',workId=w['id'],category='MATERIAL',amount='200',reference='TEST 材料凭据',note='TEST 材料成本');review(p,'costs',cost,c=fin)
adjust=act(p,'/costs',workId=w['id'],category='MATERIAL',amount='-20',originalId=cost['id'],reference='TEST 退料凭据',note='TEST 成本更正');review(p,'costs',adjust,c=fin)
act(p,'/costs',status=409,workId=w['id'],category='MATERIAL',amount='-181',originalId=cost['id'],reference='TEST',note='TEST 超额更正')
cb=bill(p,w,'CUSTOMER','12');sb=bill(p,w,'SUBCONTRACT','12','10')
review(p,'bills',cb,c=fin);review(p,'bills',sb,c=fin)
bill(p,w,'CUSTOMER','1') if False else act(p,'/bills',status=409,workId=w['id'],kind='CUSTOMER',quantity='1',retentionPercent='0',dueDate=day(15),reference='TEST 重复计量')
k=key();payment=pay(p,cb,'PAYMENT','600',request=k);replayed=pay(p,cb,'PAYMENT','600',request=k);verify(payment['id']==replayed['id'],'idempotency')
pay(p,cb,'PAYMENT','601',request=k,status=409);pay(p,cb,'PAYMENT','541',status=409)
refund=pay(p,cb,'REFUND','100',original=payment);pay(p,cb,'REVERSAL','100',original=refund)
pay(p,cb,'REFUND','601',original=payment,status=409)
verify(next(b for b in detail(p)['bills'] if b['bill']['id']==cb['id'])['net']==600,'refund and reversal preserve net')
pay(p,cb,'PAYMENT','540');pay(p,sb,'PAYMENT','216');act(p,'/state/close',status=409)
for b in [cb,sb]:act(p,'/bills/'+str(b['id'])+'/release',c=fin,reference='TEST 按合同释放保留款')
pay(p,cb,'PAYMENT','60');pay(p,sb,'PAYMENT','24');act(p,'/state/close')
d=detail(p);verify(d['project']['status']=='CLOSED','fully closed');verify(d['summary']['recognizedMargin']==780,'1200 earned minus 180 materials minus 240 subcontract');verify(all(b['balance']==0 for b in d['bills']),'zero balances');verify(all(b['bill']['releaseReference'] for b in d['bills']),'retention evidence persisted')
act(p,'/claims',c=worker,status=409,workId=w['id'],quantity='1',note='TEST',workDate=day(0))
# Leave a real persisted active TEST project for browser QA.
active=new('TEST 门店装修工程');works=[work(active,'A01','TEST 吊顶与灯具安装','80','160','55','2800'),work(active,'A02','TEST 弱电与网络布线','40','95','30','800'),work(active,'A03','TEST 配电箱安装','6','980','180','1600')]
act(active,'/members',accountId=accounts['worker']['id']);act(active,'/state/activate')
c1=claim(active,works[0],'30');review(active,'claims',c1);claim(active,works[0],'10');c2=claim(active,works[1],'12');review(active,'claims',c2)
v=act(active,'/variations',workId=works[1]['id'],quantityDelta='5',budgetDelta='100',reference='TEST 店主增项确认',reason='TEST 增设网络接口');review(active,'variations',v)
for w,category,amount in [(works[0],'MATERIAL','1850'),(works[1],'LABOR','600')]:
 co=act(active,'/costs',workId=w['id'],category=category,amount=amount,reference='TEST 成本单据',note='TEST 现场直接成本');review(active,'costs',co,c=fin)
for kind in ['CUSTOMER','SUBCONTRACT']:
 b=bill(active,works[0],kind,'30','5');review(active,'bills',b,c=fin)
 if kind=='CUSTOMER':pay(active,b,'PAYMENT','2000')
# Stale concurrent requests share the same project revision; exactly one may commit.
rev=detail(active)['project']['revision'];body=dict(revision=rev,workId=works[2]['id'],quantity='4',note='TEST 并发申报',workDate=day(0))
def concurrent_claim():
 c=Client(accounts['worker']['username'],testpw)
 req=urllib.request.Request(base+'/api/projects/'+str(active['id'])+'/claims',data=json.dumps(body).encode(),method='POST',headers={'Content-Type':'application/json',c.csrf['header']:c.csrf['token']})
 try:
  with c.opener.open(req) as response:return response.status
 except urllib.error.HTTPError as e:return e.code
with ThreadPoolExecutor(max_workers=2)as pool:codes=list(pool.map(lambda _:concurrent_claim(),range(2)))
verify(sorted(codes)==[200,409],'concurrent project lock + revision prevents overclaim')
foreigndept=admin.call('/admin/departments','POST',dict(name='TEST 其他工程部门 '+suffix))
foreign=admin.call('/admin/users','POST',dict(username='test.foreign.'+suffix,displayName='TEST foreign manager',password=testpw,roleId=role['Project manager'],departmentId=foreigndept['id'],enabled=True))
Client(foreign['username'],testpw).call('/projects/'+str(active['id']),status=403)
verify('passwordHash' not in admin.call('/admin/users')[0],'hash not returned')
admin.call('/admin/users/1','DELETE',status=409)
verify(len(admin.call('/audit'))>0,'audit persisted')
verify(len(fin.call('/reports'))==2,'reports scope')
req=urllib.request.Request(base+'/api/reports.csv')
with fin.opener.open(req) as response:csv=response.read().decode('utf-8-sig');verify('Actual cost' in csv and p['code'] in csv,'CSV report actual business')
draft=new('TEST 未签草稿');ver=detail(draft)['project']['revision'];manager.call('/projects/'+str(draft['id']),'PUT',dict(revision=ver,code=draft['code'],name='TEST 草稿改名',customer=draft['customer'],site=draft['site'],startDate=day(-6),endDate=day(30)))
manager.call('/projects/'+str(draft['id']),'DELETE',{'revision':detail(draft)['project']['revision']});manager.call('/projects/'+str(draft['id']),status=404)
state=dict(base=base,adminUsername='admin',password=password,testPassword=testpw,accounts=accounts,activeProject=active['id'],closedProject=p['id'],works=works,attachmentId=attachment['id'])
path=Path(__file__).resolve().parents[1]/'output/buildflow-quality-state.json';path.parent.mkdir(exist_ok=True)
fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
with os.fdopen(fd,'w')as f:json.dump(state,f)
print('PASS:',checks,'actual MySQL HTTP/workflow/security assertions; private QA state saved without printing credentials')
