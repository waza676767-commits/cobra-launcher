import numpy as np, sys
from PIL import Image
f32=np.float32
def length(v): return np.sqrt((v*v).sum(-1))
def norm(v): return v/np.maximum(length(v),1e-6)[...,None]
def rbox(p,b,r):
    q=np.abs(p)-np.array(b,f32)+r
    return length(np.maximum(q,0))+np.minimum(q.max(-1),0)-r
def ell(p,r):
    r=np.array(r,f32); k0=length(p/r); k1=length(p/(r*r)); return k0*(k0-1)/np.maximum(k1,1e-6)
def sph(p,r): return length(p)-r
def smin(a,b,k):
    h=np.clip(0.5+0.5*(b-a)/k,0,1); return b*(1-h)+a*h-k*h*(1-h)
def rotx(p,a):
    c,s=np.cos(a),np.sin(a); x,y,z=p[...,0],p[...,1],p[...,2]
    return np.stack([x,c*y-s*z,s*y+c*z],-1)
def roty(p,a):
    c,s=np.cos(a),np.sin(a); x,y,z=p[...,0],p[...,1],p[...,2]
    return np.stack([c*x+s*z,y,-s*x+c*z],-1)
def rotz(p,a):
    c,s=np.cos(a),np.sin(a); x,y,z=p[...,0],p[...,1],p[...,2]
    return np.stack([c*x-s*y,s*x+c*y,z],-1)
def sub(p,o): return p-np.array(o,f32)

def case(p):
    body=rbox(sub(p,(0,-0.32,0)),(1.08,0.47,0.66),0.46)
    cav=rbox(sub(p,(0,0.30,0.0)),(0.92,0.42,0.50),0.32)
    body=np.maximum(body,-cav)
    # lid hinged at back-top, opened ~105deg
    q=sub(p,(0,0.15,-0.66)); q=rotx(q,np.radians(104)); q=q-np.array((0,0.13,0.66),f32)
    lid=rbox(q,(1.08,0.20,0.66),0.20)
    lidcav=rbox(q-np.array((0,-0.16,0),f32),(0.92,0.18,0.50),0.17)
    lid=np.maximum(lid,-lidcav)
    return body,lid

def bean(p,side):
    q=sub(p,(side*0.43,0.22,0.12)); q=rotz(q,side*np.radians(-28)); q=rotx(q,np.radians(-18))
    b=ell(q,(0.40,0.31,0.30))
    tip=ell(q-np.array((side*-0.14,-0.20,0.16),f32),(0.19,0.17,0.17))
    nub=ell(q-np.array((side*0.20,0.10,-0.10),f32),(0.16,0.13,0.14))
    return smin(smin(b,tip,0.12),nub,0.10)

def stem(p,side):
    q=sub(p,(side*0.40,0.28,0.18)); q=rotz(q,side*np.radians(-12))
    head=ell(q,(0.27,0.24,0.25))
    s=q-np.array((side*0.04,-0.36,0.0),f32)
    s2=s.copy(); s2[...,1]=s[...,1]-np.clip(s[...,1],-0.32,0.32)
    st=length(s2)-0.085
    tip=ell(q-np.array((side*-0.17,-0.02,0.12),f32),(0.12,0.10,0.10))
    return smin(smin(head,st,0.10),tip,0.06)

def headphones(p):
    # headband: torus segment in xy plane
    p=roty(p,np.radians(-32))
    q=sub(p,(0,0.05,0))
    R=1.05; r=0.13
    xy=np.sqrt(q[...,0]**2+q[...,1]**2)
    band=np.sqrt((xy-R)**2+q[...,2]**2)-r
    band=np.where(q[...,1]>-0.25,band,10)
    cups=10
    for sd in (-1,1):
        c=sub(p,(sd*1.0,-0.62,0)); c=rotz(c,sd*np.radians(8))
        cup=rbox(c,(0.15,0.50,0.40),0.15)
        cup=smin(cup,ell(c-np.array((-sd*0.12,0,0),f32),(0.16,0.50,0.40)),0.06)
        cups=np.minimum(cups,cup)
        yoke=rbox(sub(p,(sd*1.06,-0.22,0)),(0.05,0.18,0.06),0.04)
        cups=np.minimum(cups,yoke)
    return band,cups

def scene(p,kind):
    if kind=="over":
        a,b=headphones(p); d=np.minimum(a,b); m=np.where(a<b,1,0)
        return d,m
    body,lid=case(p)
    if kind=="beans": l,r=bean(p,1),bean(p,-1)
    else: l,r=stem(p,1),stem(p,-1)
    buds=np.minimum(l,r)
    d=np.minimum(np.minimum(body,lid),buds)
    m=np.where(buds<=d+1e-6,1,np.where(lid<=d+1e-6,2,0))
    return d,m

def render(kind,W,H,out):
    ss=2; w,h=W*ss,H*ss
    if kind=="over": cam=np.array((0,0.5,5.6),f32); tgt=np.array((0,-0.25,0),f32); fov=0.42
    else: cam=np.array((0,1.55,5.0),f32); tgt=np.array((0,0.05,0),f32); fov=0.33
    fw=norm(tgt-cam); rt=norm(np.cross(fw,np.array((0,1,0),f32))); up=np.cross(rt,fw)
    ys,xs=np.mgrid[0:h,0:w].astype(f32)
    u=(xs-w/2)/h*2*fov; v=-(ys-h/2)/h*2*fov
    rd=norm(fw+u[...,None]*rt+v[...,None]*up).reshape(-1,3)
    ro=np.broadcast_to(cam,rd.shape)
    n=rd.shape[0]; t=np.zeros(n,f32); hit=np.zeros(n,bool); act=np.ones(n,bool)
    for i in range(140):
        idx=np.nonzero(act)[0]
        if idx.size==0: break
        p=ro[idx]+rd[idx]*t[idx,None]
        d,_=scene(p,kind)
        t[idx]+=d*0.9
        h_=d<0.0008; far=t[idx]>12
        hit[idx[h_]]=True; act[idx[h_|far]]=False
    col=np.zeros((n,3),f32); alpha=np.zeros(n,f32)
    idx=np.nonzero(hit)[0]
    p=ro[idx]+rd[idx]*t[idx,None]
    e=0.0015
    def D(q): return scene(q,kind)[0]
    nrm=norm(np.stack([D(p+np.array((e,0,0),f32))-D(p-np.array((e,0,0),f32)),
                       D(p+np.array((0,e,0),f32))-D(p-np.array((0,e,0),f32)),
                       D(p+np.array((0,0,e),f32))-D(p-np.array((0,0,e),f32))],-1))
    _,mat=scene(p,kind)
    # AO
    ao=np.ones(len(idx),f32)
    for k in range(1,6):
        hh=0.04*k; ao-= (hh-D(p+nrm*hh))*(0.9/k)
    ao=np.clip(ao,0.15,1)
    # soft shadow toward key light
    L=norm(np.array((-0.55,1.0,0.55),f32))
    sh=np.ones(len(idx),f32); tt=np.full(len(idx),0.02,f32)
    for k in range(48):
        dd=D(p+nrm*0.004+L*tt[:,None]); sh=np.minimum(sh,10*dd/tt); tt+=np.clip(dd,0.01,0.2)
    sh=np.clip(sh,0,1); sh=sh*sh*(3-2*sh)
    V=-rd[idx]
    base=np.array([(0.034,0.036,0.040),(0.030,0.032,0.036),(0.026,0.027,0.030)],f32)[mat]
    inside=(mat!=1)&(np.abs(p[:,0])<0.93)&(np.abs(p[:,2])<0.52)&(p[:,1]>-0.45)&(p[:,1]<0.2)&(nrm[:,1]<0.9)
    base=np.where(inside[:,None],base*0.35,base)
    # darker interior of cavities (surfaces facing up-inside get occluded by AO already)
    diff=np.clip((nrm*L).sum(-1),0,1)*sh
    fill=np.clip((nrm*norm(np.array((0.8,0.3,0.6),f32))).sum(-1),0,1)*0.35
    sky=(0.5+0.5*nrm[:,1])*0.25
    Hh=norm(L+V); spec=np.clip((nrm*Hh).sum(-1),0,1)**48*sh*0.55
    Hf=norm(norm(np.array((0.8,0.3,0.6),f32))+V); spec+=np.clip((nrm*Hf).sum(-1),0,1)**30*0.12
    fres=(1-np.clip((nrm*V).sum(-1),0,1))**4
    rim=fres*0.35*(0.4+0.6*np.clip(nrm[:,1]*0.5+0.5,0,1))
    c=base*(diff[:,None]*2.6+fill[:,None]+sky[:,None])*ao[:,None]+spec[:,None]*np.array((0.9,0.95,1),f32)+rim[:,None]*np.array((0.55,0.62,0.7),f32)*ao[:,None]
    if kind!="over":
        led=(mat==0)&(np.abs(p[:,0])<0.16)&(np.abs(p[:,1]+0.13)<0.014)&(nrm[:,2]>0.6)
        c=np.where(led[:,None],np.array((0.92,0.95,1.0),f32),c)
    col[idx]=c; alpha[idx]=1
    # contact shadow on invisible floor
    floor_y=-0.80 if kind!="over" else -1.06
    miss=np.nonzero(~hit & (rd[:,1]<0))[0]
    tf=(floor_y-ro[miss,1])/rd[miss,1]
    fp=ro[miss]+rd[miss]*tf[:,None]
    occ=np.zeros(len(miss),f32)
    for k in range(1,7):
        hh=0.06*k; occ+=np.clip((hh-D(fp+np.array((0,hh,0),f32)))/hh,0,1)*(1.0/k)
    a=np.clip(occ*0.45,0,0.85)
    alpha[miss]=a; col[miss]=0
    img=np.concatenate([np.clip(col,0,1)**(1/2.2),alpha[:,None]],-1).reshape(h,w,4)
    im=Image.fromarray((img*255).astype(np.uint8),"RGBA").resize((W,H),Image.LANCZOS)
    im.save(out)
kind=sys.argv[1]; render(kind,int(sys.argv[2]),int(sys.argv[3]),sys.argv[4])
