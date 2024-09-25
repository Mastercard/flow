"use strict";var _t=($,X,Ee)=>{if(X.has($))throw TypeError("Cannot add the same private member more than once");X instanceof WeakSet?X.add($):X.set($,Ee)};(self.webpackChunkreport=self.webpackChunkreport||[]).push([[429],{8697:($,X,Ee)=>{Ee(8583)},8583:()=>{const $=globalThis;function X(e){return($.__Zone_symbol_prefix||"__zone_symbol__")+e}const Te=Object.getOwnPropertyDescriptor,Le=Object.defineProperty,Me=Object.getPrototypeOf,Et=Object.create,Tt=Array.prototype.slice,Ie="addEventListener",Ze="removeEventListener",Ae=X(Ie),je=X(Ze),ue="true",fe="false",Pe=X("");function He(e,n){return Zone.current.wrap(e,n)}function xe(e,n,a,t,c){return Zone.current.scheduleMacroTask(e,n,a,t,c)}const Z=X,Ce=typeof window<"u",ge=Ce?window:void 0,Y=Ce&&ge||globalThis;function Ve(e,n){for(let a=e.length-1;a>=0;a--)"function"==typeof e[a]&&(e[a]=He(e[a],n+"_"+a));return e}function We(e){return!e||!1!==e.writable&&!("function"==typeof e.get&&typeof e.set>"u")}const qe=typeof WorkerGlobalScope<"u"&&self instanceof WorkerGlobalScope,De=!("nw"in Y)&&typeof Y.process<"u"&&"[object process]"===Y.process.toString(),Ge=!De&&!qe&&!(!Ce||!ge.HTMLElement),Xe=typeof Y.process<"u"&&"[object process]"===Y.process.toString()&&!qe&&!(!Ce||!ge.HTMLElement),Se={},pt=Z("enable_beforeunload"),Ye=function(e){if(!(e=e||Y.event))return;let n=Se[e.type];n||(n=Se[e.type]=Z("ON_PROPERTY"+e.type));const a=this||e.target||Y,t=a[n];let c;if(Ge&&a===ge&&"error"===e.type){const h=e;c=t&&t.call(this,h.message,h.filename,h.lineno,h.colno,h.error),!0===c&&e.preventDefault()}else c=t&&t.apply(this,arguments),"beforeunload"===e.type&&Y[pt]&&"string"==typeof c?e.returnValue=c:null!=c&&!c&&e.preventDefault();return c};function $e(e,n,a){let t=Te(e,n);if(!t&&a&&Te(a,n)&&(t={enumerable:!0,configurable:!0}),!t||!t.configurable)return;const c=Z("on"+n+"patched");if(e.hasOwnProperty(c)&&e[c])return;delete t.writable,delete t.value;const h=t.get,E=t.set,T=n.slice(2);let m=Se[T];m||(m=Se[T]=Z("ON_PROPERTY"+T)),t.set=function(D){let _=this;!_&&e===Y&&(_=Y),_&&("function"==typeof _[m]&&_.removeEventListener(T,Ye),E&&E.call(_,null),_[m]=D,"function"==typeof D&&_.addEventListener(T,Ye,!1))},t.get=function(){let D=this;if(!D&&e===Y&&(D=Y),!D)return null;const _=D[m];if(_)return _;if(h){let w=h.call(this);if(w)return t.set.call(this,w),"function"==typeof D.removeAttribute&&D.removeAttribute(n),w}return null},Le(e,n,t),e[c]=!0}function Ke(e,n,a){if(n)for(let t=0;t<n.length;t++)$e(e,"on"+n[t],a);else{const t=[];for(const c in e)"on"==c.slice(0,2)&&t.push(c);for(let c=0;c<t.length;c++)$e(e,t[c],a)}}const re=Z("originalInstance");function we(e){const n=Y[e];if(!n)return;Y[Z(e)]=n,Y[e]=function(){const c=Ve(arguments,e);switch(c.length){case 0:this[re]=new n;break;case 1:this[re]=new n(c[0]);break;case 2:this[re]=new n(c[0],c[1]);break;case 3:this[re]=new n(c[0],c[1],c[2]);break;case 4:this[re]=new n(c[0],c[1],c[2],c[3]);break;default:throw new Error("Arg list too long.")}},de(Y[e],n);const a=new n(function(){});let t;for(t in a)"XMLHttpRequest"===e&&"responseBlob"===t||function(c){"function"==typeof a[c]?Y[e].prototype[c]=function(){return this[re][c].apply(this[re],arguments)}:Le(Y[e].prototype,c,{set:function(h){"function"==typeof h?(this[re][c]=He(h,e+"."+c),de(this[re][c],h)):this[re][c]=h},get:function(){return this[re][c]}})}(t);for(t in n)"prototype"!==t&&n.hasOwnProperty(t)&&(Y[e][t]=n[t])}function he(e,n,a){let t=e;for(;t&&!t.hasOwnProperty(n);)t=Me(t);!t&&e[n]&&(t=e);const c=Z(n);let h=null;if(t&&(!(h=t[c])||!t.hasOwnProperty(c))&&(h=t[c]=t[n],We(t&&Te(t,n)))){const T=a(h,c,n);t[n]=function(){return T(this,arguments)},de(t[n],h)}return h}function mt(e,n,a){let t=null;function c(h){const E=h.data;return E.args[E.cbIdx]=function(){h.invoke.apply(this,arguments)},t.apply(E.target,E.args),h}t=he(e,n,h=>function(E,T){const m=a(E,T);return m.cbIdx>=0&&"function"==typeof T[m.cbIdx]?xe(m.name,T[m.cbIdx],m,c):h.apply(E,T)})}function de(e,n){e[Z("OriginalDelegate")]=n}let Je=!1,Be=!1;function vt(){if(Je)return Be;Je=!0;try{const e=ge.navigator.userAgent;(-1!==e.indexOf("MSIE ")||-1!==e.indexOf("Trident/")||-1!==e.indexOf("Edge/"))&&(Be=!0)}catch{}return Be}function Qe(e){return"function"==typeof e}function et(e){return"number"==typeof e}let ye=!1;if(typeof window<"u")try{const e=Object.defineProperty({},"passive",{get:function(){ye=!0}});window.addEventListener("test",e,e),window.removeEventListener("test",e,e)}catch{ye=!1}const bt={useG:!0},ne={},tt={},nt=new RegExp("^"+Pe+"(\\w+)(true|false)$"),rt=Z("propagationStopped");function ot(e,n){const a=(n?n(e):e)+fe,t=(n?n(e):e)+ue,c=Pe+a,h=Pe+t;ne[e]={},ne[e][fe]=c,ne[e][ue]=h}function Pt(e,n,a,t){const c=t&&t.add||Ie,h=t&&t.rm||Ze,E=t&&t.listeners||"eventListeners",T=t&&t.rmAll||"removeAllListeners",m=Z(c),D="."+c+":",I=function(k,d,A){if(k.isRemoved)return;const H=k.callback;let q;"object"==typeof H&&H.handleEvent&&(k.callback=g=>H.handleEvent(g),k.originalDelegate=H);try{k.invoke(k,d,[A])}catch(g){q=g}const V=k.options;return V&&"object"==typeof V&&V.once&&d[h].call(d,A.type,k.originalDelegate?k.originalDelegate:k.callback,V),q};function j(k,d,A){if(!(d=d||e.event))return;const H=k||d.target||e,q=H[ne[d.type][A?ue:fe]];if(q){const V=[];if(1===q.length){const g=I(q[0],H,d);g&&V.push(g)}else{const g=q.slice();for(let U=0;U<g.length&&(!d||!0!==d[rt]);U++){const O=I(g[U],H,d);O&&V.push(O)}}if(1===V.length)throw V[0];for(let g=0;g<V.length;g++){const U=V[g];n.nativeScheduleMicroTask(()=>{throw U})}}}const F=function(k){return j(this,k,!1)},J=function(k){return j(this,k,!0)};function Q(k,d){if(!k)return!1;let A=!0;d&&void 0!==d.useG&&(A=d.useG);const H=d&&d.vh;let q=!0;d&&void 0!==d.chkDup&&(q=d.chkDup);let V=!1;d&&void 0!==d.rt&&(V=d.rt);let g=k;for(;g&&!g.hasOwnProperty(c);)g=Me(g);if(!g&&k[c]&&(g=k),!g||g[m])return!1;const U=d&&d.eventNameToString,O={},R=g[m]=g[c],P=g[Z(h)]=g[h],S=g[Z(E)]=g[E],ee=g[Z(T)]=g[T];let z;function K(r,f){return!ye&&"object"==typeof r&&r?!!r.capture:ye&&f?"boolean"==typeof r?{capture:r,passive:!0}:r?"object"==typeof r&&!1!==r.passive?{...r,passive:!0}:r:{passive:!0}:r}d&&d.prepend&&(z=g[Z(d.prepend)]=g[d.prepend]);const y=A?function(r){if(!O.isExisting)return R.call(O.target,O.eventName,O.capture?J:F,O.options)}:function(r){return R.call(O.target,O.eventName,r.invoke,O.options)},v=A?function(r){if(!r.isRemoved){const f=ne[r.eventName];let b;f&&(b=f[r.capture?ue:fe]);const C=b&&r.target[b];if(C)for(let p=0;p<C.length;p++)if(C[p]===r){C.splice(p,1),r.isRemoved=!0,r.removeAbortListener&&(r.removeAbortListener(),r.removeAbortListener=null),0===C.length&&(r.allRemoved=!0,r.target[b]=null);break}}if(r.allRemoved)return P.call(r.target,r.eventName,r.capture?J:F,r.options)}:function(r){return P.call(r.target,r.eventName,r.invoke,r.options)},se=d&&d.diff?d.diff:function(r,f){const b=typeof f;return"function"===b&&r.callback===f||"object"===b&&r.originalDelegate===f},ie=Zone[Z("UNPATCHED_EVENTS")],me=e[Z("PASSIVE_EVENTS")];function u(r){if("object"==typeof r&&null!==r){const f={...r};return r.signal&&(f.signal=r.signal),f}return r}const l=function(r,f,b,C,p=!1,N=!1){return function(){const L=this||e;let M=arguments[0];d&&d.transferEventName&&(M=d.transferEventName(M));let G=arguments[1];if(!G)return r.apply(this,arguments);if(De&&"uncaughtException"===M)return r.apply(this,arguments);let B=!1;if("function"!=typeof G){if(!G.handleEvent)return r.apply(this,arguments);B=!0}if(H&&!H(r,G,L,arguments))return;const _e=ye&&!!me&&-1!==me.indexOf(M),ae=u(K(arguments[2],_e)),ke=ae?.signal;if(ke?.aborted)return;if(ie)for(let le=0;le<ie.length;le++)if(M===ie[le])return _e?r.call(L,M,G,ae):r.apply(this,arguments);const Ue=!!ae&&("boolean"==typeof ae||ae.capture),lt=!(!ae||"object"!=typeof ae)&&ae.once,jt=Zone.current;let ze=ne[M];ze||(ot(M,U),ze=ne[M]);const ut=ze[Ue?ue:fe];let Ne,ve=L[ut],ft=!1;if(ve){if(ft=!0,q)for(let le=0;le<ve.length;le++)if(se(ve[le],G))return}else ve=L[ut]=[];const ht=L.constructor.name,dt=tt[ht];dt&&(Ne=dt[M]),Ne||(Ne=ht+f+(U?U(M):M)),O.options=ae,lt&&(O.options.once=!1),O.target=L,O.capture=Ue,O.eventName=M,O.isExisting=ft;const Re=A?bt:void 0;Re&&(Re.taskData=O),ke&&(O.options.signal=void 0);const ce=jt.scheduleEventTask(Ne,G,Re,b,C);if(ke){O.options.signal=ke;const le=()=>ce.zone.cancelTask(ce);r.call(ke,"abort",le,{once:!0}),ce.removeAbortListener=()=>ke.removeEventListener("abort",le)}return O.target=null,Re&&(Re.taskData=null),lt&&(O.options.once=!0),!ye&&"boolean"==typeof ce.options||(ce.options=ae),ce.target=L,ce.capture=Ue,ce.eventName=M,B&&(ce.originalDelegate=G),N?ve.unshift(ce):ve.push(ce),p?L:void 0}};return g[c]=l(R,D,y,v,V),z&&(g.prependListener=l(z,".prependListener:",function(r){return z.call(O.target,O.eventName,r.invoke,O.options)},v,V,!0)),g[h]=function(){const r=this||e;let f=arguments[0];d&&d.transferEventName&&(f=d.transferEventName(f));const b=arguments[2],C=!!b&&("boolean"==typeof b||b.capture),p=arguments[1];if(!p)return P.apply(this,arguments);if(H&&!H(P,p,r,arguments))return;const N=ne[f];let L;N&&(L=N[C?ue:fe]);const M=L&&r[L];if(M)for(let G=0;G<M.length;G++){const B=M[G];if(se(B,p))return M.splice(G,1),B.isRemoved=!0,0!==M.length||(B.allRemoved=!0,r[L]=null,C||"string"!=typeof f)||(r[Pe+"ON_PROPERTY"+f]=null),B.zone.cancelTask(B),V?r:void 0}return P.apply(this,arguments)},g[E]=function(){const r=this||e;let f=arguments[0];d&&d.transferEventName&&(f=d.transferEventName(f));const b=[],C=st(r,U?U(f):f);for(let p=0;p<C.length;p++){const N=C[p];b.push(N.originalDelegate?N.originalDelegate:N.callback)}return b},g[T]=function(){const r=this||e;let f=arguments[0];if(f){d&&d.transferEventName&&(f=d.transferEventName(f));const b=ne[f];if(b){const N=r[b[fe]],L=r[b[ue]];if(N){const M=N.slice();for(let G=0;G<M.length;G++){const B=M[G];this[h].call(this,f,B.originalDelegate?B.originalDelegate:B.callback,B.options)}}if(L){const M=L.slice();for(let G=0;G<M.length;G++){const B=M[G];this[h].call(this,f,B.originalDelegate?B.originalDelegate:B.callback,B.options)}}}}else{const b=Object.keys(r);for(let C=0;C<b.length;C++){const N=nt.exec(b[C]);let L=N&&N[1];L&&"removeListener"!==L&&this[T].call(this,L)}this[T].call(this,"removeListener")}if(V)return this},de(g[c],R),de(g[h],P),ee&&de(g[T],ee),S&&de(g[E],S),!0}let W=[];for(let k=0;k<a.length;k++)W[k]=Q(a[k],t);return W}function st(e,n){if(!n){const h=[];for(let E in e){const T=nt.exec(E);let m=T&&T[1];if(m&&(!n||m===n)){const D=e[E];if(D)for(let _=0;_<D.length;_++)h.push(D[_])}}return h}let a=ne[n];a||(ot(n),a=ne[n]);const t=e[a[fe]],c=e[a[ue]];return t?c?t.concat(c):t.slice():c?c.slice():[]}function wt(e,n){const a=e.Event;a&&a.prototype&&n.patchMethod(a.prototype,"stopImmediatePropagation",t=>function(c,h){c[rt]=!0,t&&t.apply(c,h)})}const Oe=Z("zoneTask");function pe(e,n,a,t){let c=null,h=null;a+=t;const E={};function T(D){const _=D.data;_.args[0]=function(){return D.invoke.apply(this,arguments)};const w=c.apply(e,_.args);return et(w)?_.handleId=w:(_.handle=w,_.isRefreshable=Qe(w.refresh)),D}function m(D){const{handle:_,handleId:w}=D.data;return h.call(e,_??w)}c=he(e,n+=t,D=>function(_,w){if(Qe(w[0])){const I={isRefreshable:!1,isPeriodic:"Interval"===t,delay:"Timeout"===t||"Interval"===t?w[1]||0:void 0,args:w},j=w[0];w[0]=function(){try{return j.apply(this,arguments)}finally{const{handle:A,handleId:H,isPeriodic:q,isRefreshable:V}=I;!q&&!V&&(H?delete E[H]:A&&(A[Oe]=null))}};const F=xe(n,w[0],I,T,m);if(!F)return F;const{handleId:J,handle:Q,isRefreshable:W,isPeriodic:k}=F.data;if(J)E[J]=F;else if(Q&&(Q[Oe]=F,W&&!k)){const d=Q.refresh;Q.refresh=function(){const{zone:A,state:H}=F;return"notScheduled"===H?(F._state="scheduled",A._updateTaskCount(F,1)):"running"===H&&(F._state="scheduling"),d.call(this)}}return Q??J??F}return D.apply(e,w)}),h=he(e,a,D=>function(_,w){const I=w[0];let j;et(I)?(j=E[I],delete E[I]):(j=I?.[Oe],j?I[Oe]=null:j=I),j?.type?j.cancelFn&&j.zone.cancelTask(j):D.apply(e,w)})}function it(e,n,a){if(!a||0===a.length)return n;const t=a.filter(h=>h.target===e);if(!t||0===t.length)return n;const c=t[0].ignoreProperties;return n.filter(h=>-1===c.indexOf(h))}function ct(e,n,a,t){e&&Ke(e,it(e,n,a),t)}function Fe(e){return Object.getOwnPropertyNames(e).filter(n=>n.startsWith("on")&&n.length>2).map(n=>n.substring(2))}function It(e,n,a,t,c){const h=Zone.__symbol__(t);if(n[h])return;const E=n[h]=n[t];n[t]=function(T,m,D){return m&&m.prototype&&c.forEach(function(_){const w=`${a}.${t}::`+_,I=m.prototype;try{if(I.hasOwnProperty(_)){const j=e.ObjectGetOwnPropertyDescriptor(I,_);j&&j.value?(j.value=e.wrapWithCurrentZone(j.value,w),e._redefineProperty(m.prototype,_,j)):I[_]&&(I[_]=e.wrapWithCurrentZone(I[_],w))}else I[_]&&(I[_]=e.wrapWithCurrentZone(I[_],w))}catch{}}),E.call(n,T,m,D)},e.attachOriginToPatched(n[t],E)}const at=function be(){const e=globalThis,n=!0===e[X("forceDuplicateZoneCheck")];if(e.Zone&&(n||"function"!=typeof e.Zone.__symbol__))throw new Error("Zone already loaded.");return e.Zone??(e.Zone=function Ee(){var K;const e=$.performance;function n(x){e&&e.mark&&e.mark(x)}function a(x,o){e&&e.measure&&e.measure(x,o)}n("Zone");const te=class{static assertZonePatched(){if($.Promise!==O.ZoneAwarePromise)throw new Error("Zone.js has detected that ZoneAwarePromise `(window|global).Promise` has been overwritten.\nMost likely cause is that a Promise polyfill has been loaded after Zone.js (Polyfilling Promise api is not necessary when zone.js is loaded. If you must load one, do so before loading zone.js.)")}static get root(){let o=te.current;for(;o.parent;)o=o.parent;return o}static get current(){return P.zone}static get currentTask(){return S}static __load_patch(o,s,i=!1){if(O.hasOwnProperty(o)){const y=!0===$[X("forceDuplicateZoneCheck")];if(!i&&y)throw Error("Already loaded patch: "+o)}else if(!$["__Zone_disable_"+o]){const y="Zone:"+o;n(y),O[o]=s($,te,R),a(y,y)}}get parent(){return this._parent}get name(){return this._name}constructor(o,s){this._parent=o,this._name=s?s.name||"unnamed":"<root>",this._properties=s&&s.properties||{},this._zoneDelegate=new h(this,this._parent&&this._parent._zoneDelegate,s)}get(o){const s=this.getZoneWith(o);if(s)return s._properties[o]}getZoneWith(o){let s=this;for(;s;){if(s._properties.hasOwnProperty(o))return s;s=s._parent}return null}fork(o){if(!o)throw new Error("ZoneSpec required!");return this._zoneDelegate.fork(this,o)}wrap(o,s){if("function"!=typeof o)throw new Error("Expecting function got: "+o);const i=this._zoneDelegate.intercept(this,o,s),y=this;return function(){return y.runGuarded(i,this,arguments,s)}}run(o,s,i,y){P={parent:P,zone:this};try{return this._zoneDelegate.invoke(this,o,s,i,y)}finally{P=P.parent}}runGuarded(o,s=null,i,y){P={parent:P,zone:this};try{try{return this._zoneDelegate.invoke(this,o,s,i,y)}catch(v){if(this._zoneDelegate.handleError(this,v))throw v}}finally{P=P.parent}}runTask(o,s,i){if(o.zone!=this)throw new Error("A task can only be run in the zone of creation! (Creation: "+(o.zone||Q).name+"; Execution: "+this.name+")");const y=o,{type:v,data:{isPeriodic:oe=!1,isRefreshable:se=!1}={}}=o;if(o.state===W&&(v===U||v===g))return;const ie=o.state!=A;ie&&y._transitionTo(A,d);const me=S;S=y,P={parent:P,zone:this};try{v==g&&o.data&&!oe&&!se&&(o.cancelFn=void 0);try{return this._zoneDelegate.invokeTask(this,y,s,i)}catch(u){if(this._zoneDelegate.handleError(this,u))throw u}}finally{const u=o.state;if(u!==W&&u!==q)if(v==U||oe||se&&u===k)ie&&y._transitionTo(d,A,k);else{const l=y._zoneDelegates;this._updateTaskCount(y,-1),ie&&y._transitionTo(W,A,W),se&&(y._zoneDelegates=l)}P=P.parent,S=me}}scheduleTask(o){if(o.zone&&o.zone!==this){let i=this;for(;i;){if(i===o.zone)throw Error(`can not reschedule task to ${this.name} which is descendants of the original zone ${o.zone.name}`);i=i.parent}}o._transitionTo(k,W);const s=[];o._zoneDelegates=s,o._zone=this;try{o=this._zoneDelegate.scheduleTask(this,o)}catch(i){throw o._transitionTo(q,k,W),this._zoneDelegate.handleError(this,i),i}return o._zoneDelegates===s&&this._updateTaskCount(o,1),o.state==k&&o._transitionTo(d,k),o}scheduleMicroTask(o,s,i,y){return this.scheduleTask(new E(V,o,s,i,y,void 0))}scheduleMacroTask(o,s,i,y,v){return this.scheduleTask(new E(g,o,s,i,y,v))}scheduleEventTask(o,s,i,y,v){return this.scheduleTask(new E(U,o,s,i,y,v))}cancelTask(o){if(o.zone!=this)throw new Error("A task can only be cancelled in the zone of creation! (Creation: "+(o.zone||Q).name+"; Execution: "+this.name+")");if(o.state===d||o.state===A){o._transitionTo(H,d,A);try{this._zoneDelegate.cancelTask(this,o)}catch(s){throw o._transitionTo(q,H),this._zoneDelegate.handleError(this,s),s}return this._updateTaskCount(o,-1),o._transitionTo(W,H),o.runCount=-1,o}}_updateTaskCount(o,s){const i=o._zoneDelegates;-1==s&&(o._zoneDelegates=null);for(let y=0;y<i.length;y++)i[y]._updateTaskCount(o.type,s)}};let t=te;K=new WeakMap,_t(t,K,te.__symbol__=X);const c={name:"",onHasTask:(x,o,s,i)=>x.hasTask(s,i),onScheduleTask:(x,o,s,i)=>x.scheduleTask(s,i),onInvokeTask:(x,o,s,i,y,v)=>x.invokeTask(s,i,y,v),onCancelTask:(x,o,s,i)=>x.cancelTask(s,i)};class h{get zone(){return this._zone}constructor(o,s,i){this._taskCounts={microTask:0,macroTask:0,eventTask:0},this._zone=o,this._parentDelegate=s,this._forkZS=i&&(i&&i.onFork?i:s._forkZS),this._forkDlgt=i&&(i.onFork?s:s._forkDlgt),this._forkCurrZone=i&&(i.onFork?this._zone:s._forkCurrZone),this._interceptZS=i&&(i.onIntercept?i:s._interceptZS),this._interceptDlgt=i&&(i.onIntercept?s:s._interceptDlgt),this._interceptCurrZone=i&&(i.onIntercept?this._zone:s._interceptCurrZone),this._invokeZS=i&&(i.onInvoke?i:s._invokeZS),this._invokeDlgt=i&&(i.onInvoke?s:s._invokeDlgt),this._invokeCurrZone=i&&(i.onInvoke?this._zone:s._invokeCurrZone),this._handleErrorZS=i&&(i.onHandleError?i:s._handleErrorZS),this._handleErrorDlgt=i&&(i.onHandleError?s:s._handleErrorDlgt),this._handleErrorCurrZone=i&&(i.onHandleError?this._zone:s._handleErrorCurrZone),this._scheduleTaskZS=i&&(i.onScheduleTask?i:s._scheduleTaskZS),this._scheduleTaskDlgt=i&&(i.onScheduleTask?s:s._scheduleTaskDlgt),this._scheduleTaskCurrZone=i&&(i.onScheduleTask?this._zone:s._scheduleTaskCurrZone),this._invokeTaskZS=i&&(i.onInvokeTask?i:s._invokeTaskZS),this._invokeTaskDlgt=i&&(i.onInvokeTask?s:s._invokeTaskDlgt),this._invokeTaskCurrZone=i&&(i.onInvokeTask?this._zone:s._invokeTaskCurrZone),this._cancelTaskZS=i&&(i.onCancelTask?i:s._cancelTaskZS),this._cancelTaskDlgt=i&&(i.onCancelTask?s:s._cancelTaskDlgt),this._cancelTaskCurrZone=i&&(i.onCancelTask?this._zone:s._cancelTaskCurrZone),this._hasTaskZS=null,this._hasTaskDlgt=null,this._hasTaskDlgtOwner=null,this._hasTaskCurrZone=null;const y=i&&i.onHasTask;(y||s&&s._hasTaskZS)&&(this._hasTaskZS=y?i:c,this._hasTaskDlgt=s,this._hasTaskDlgtOwner=this,this._hasTaskCurrZone=this._zone,i.onScheduleTask||(this._scheduleTaskZS=c,this._scheduleTaskDlgt=s,this._scheduleTaskCurrZone=this._zone),i.onInvokeTask||(this._invokeTaskZS=c,this._invokeTaskDlgt=s,this._invokeTaskCurrZone=this._zone),i.onCancelTask||(this._cancelTaskZS=c,this._cancelTaskDlgt=s,this._cancelTaskCurrZone=this._zone))}fork(o,s){return this._forkZS?this._forkZS.onFork(this._forkDlgt,this.zone,o,s):new t(o,s)}intercept(o,s,i){return this._interceptZS?this._interceptZS.onIntercept(this._interceptDlgt,this._interceptCurrZone,o,s,i):s}invoke(o,s,i,y,v){return this._invokeZS?this._invokeZS.onInvoke(this._invokeDlgt,this._invokeCurrZone,o,s,i,y,v):s.apply(i,y)}handleError(o,s){return!this._handleErrorZS||this._handleErrorZS.onHandleError(this._handleErrorDlgt,this._handleErrorCurrZone,o,s)}scheduleTask(o,s){let i=s;if(this._scheduleTaskZS)this._hasTaskZS&&i._zoneDelegates.push(this._hasTaskDlgtOwner),i=this._scheduleTaskZS.onScheduleTask(this._scheduleTaskDlgt,this._scheduleTaskCurrZone,o,s),i||(i=s);else if(s.scheduleFn)s.scheduleFn(s);else{if(s.type!=V)throw new Error("Task is missing scheduleFn.");F(s)}return i}invokeTask(o,s,i,y){return this._invokeTaskZS?this._invokeTaskZS.onInvokeTask(this._invokeTaskDlgt,this._invokeTaskCurrZone,o,s,i,y):s.callback.apply(i,y)}cancelTask(o,s){let i;if(this._cancelTaskZS)i=this._cancelTaskZS.onCancelTask(this._cancelTaskDlgt,this._cancelTaskCurrZone,o,s);else{if(!s.cancelFn)throw Error("Task is not cancelable");i=s.cancelFn(s)}return i}hasTask(o,s){try{this._hasTaskZS&&this._hasTaskZS.onHasTask(this._hasTaskDlgt,this._hasTaskCurrZone,o,s)}catch(i){this.handleError(o,i)}}_updateTaskCount(o,s){const i=this._taskCounts,y=i[o],v=i[o]=y+s;if(v<0)throw new Error("More tasks executed then were scheduled.");0!=y&&0!=v||this.hasTask(this._zone,{microTask:i.microTask>0,macroTask:i.macroTask>0,eventTask:i.eventTask>0,change:o})}}class E{constructor(o,s,i,y,v,oe){if(this._zone=null,this.runCount=0,this._zoneDelegates=null,this._state="notScheduled",this.type=o,this.source=s,this.data=y,this.scheduleFn=v,this.cancelFn=oe,!i)throw new Error("callback is not defined");this.callback=i;const se=this;this.invoke=o===U&&y&&y.useG?E.invokeTask:function(){return E.invokeTask.call($,se,this,arguments)}}static invokeTask(o,s,i){o||(o=this),ee++;try{return o.runCount++,o.zone.runTask(o,s,i)}finally{1==ee&&J(),ee--}}get zone(){return this._zone}get state(){return this._state}cancelScheduleRequest(){this._transitionTo(W,k)}_transitionTo(o,s,i){if(this._state!==s&&this._state!==i)throw new Error(`${this.type} '${this.source}': can not transition to '${o}', expecting state '${s}'${i?" or '"+i+"'":""}, was '${this._state}'.`);this._state=o,o==W&&(this._zoneDelegates=null)}toString(){return this.data&&typeof this.data.handleId<"u"?this.data.handleId.toString():Object.prototype.toString.call(this)}toJSON(){return{type:this.type,state:this.state,source:this.source,zone:this.zone.name,runCount:this.runCount}}}const T=X("setTimeout"),m=X("Promise"),D=X("then");let I,_=[],w=!1;function j(x){if(I||$[m]&&(I=$[m].resolve(0)),I){let o=I[D];o||(o=I.then),o.call(I,x)}else $[T](x,0)}function F(x){0===ee&&0===_.length&&j(J),x&&_.push(x)}function J(){if(!w){for(w=!0;_.length;){const x=_;_=[];for(let o=0;o<x.length;o++){const s=x[o];try{s.zone.runTask(s,null,null)}catch(i){R.onUnhandledError(i)}}}R.microtaskDrainDone(),w=!1}}const Q={name:"NO ZONE"},W="notScheduled",k="scheduling",d="scheduled",A="running",H="canceling",q="unknown",V="microTask",g="macroTask",U="eventTask",O={},R={symbol:X,currentZoneFrame:()=>P,onUnhandledError:z,microtaskDrainDone:z,scheduleMicroTask:F,showUncaughtError:()=>!t[X("ignoreConsoleErrorUncaughtError")],patchEventTarget:()=>[],patchOnProperties:z,patchMethod:()=>z,bindArguments:()=>[],patchThen:()=>z,patchMacroTask:()=>z,patchEventPrototype:()=>z,isIEOrEdge:()=>!1,getGlobalObjects:()=>{},ObjectDefineProperty:()=>z,ObjectGetOwnPropertyDescriptor:()=>{},ObjectCreate:()=>{},ArraySlice:()=>[],patchClass:()=>z,wrapWithCurrentZone:()=>z,filterProperties:()=>[],attachOriginToPatched:()=>z,_redefineProperty:()=>z,patchCallbacks:()=>z,nativeScheduleMicroTask:j};let P={parent:null,zone:new t(null,null)},S=null,ee=0;function z(){}return a("Zone","Zone"),t}()),e.Zone}();(function At(e){(function Lt(e){e.__load_patch("ZoneAwarePromise",(n,a,t)=>{const c=Object.getOwnPropertyDescriptor,h=Object.defineProperty,T=t.symbol,m=[],D=!1!==n[T("DISABLE_WRAPPING_UNCAUGHT_PROMISE_REJECTION")],_=T("Promise"),w=T("then");t.onUnhandledError=u=>{if(t.showUncaughtError()){const l=u&&u.rejection;l?console.error("Unhandled Promise rejection:",l instanceof Error?l.message:l,"; Zone:",u.zone.name,"; Task:",u.task&&u.task.source,"; Value:",l,l instanceof Error?l.stack:void 0):console.error(u)}},t.microtaskDrainDone=()=>{for(;m.length;){const u=m.shift();try{u.zone.runGuarded(()=>{throw u.throwOriginal?u.rejection:u})}catch(l){F(l)}}};const j=T("unhandledPromiseRejectionHandler");function F(u){t.onUnhandledError(u);try{const l=a[j];"function"==typeof l&&l.call(this,u)}catch{}}function J(u){return u&&u.then}function Q(u){return u}function W(u){return v.reject(u)}const k=T("state"),d=T("value"),A=T("finally"),H=T("parentPromiseValue"),q=T("parentPromiseState"),g=null,U=!0,O=!1;function P(u,l){return r=>{try{K(u,l,r)}catch(f){K(u,!1,f)}}}const S=function(){let u=!1;return function(r){return function(){u||(u=!0,r.apply(null,arguments))}}},z=T("currentTaskTrace");function K(u,l,r){const f=S();if(u===r)throw new TypeError("Promise resolved with itself");if(u[k]===g){let b=null;try{("object"==typeof r||"function"==typeof r)&&(b=r&&r.then)}catch(C){return f(()=>{K(u,!1,C)})(),u}if(l!==O&&r instanceof v&&r.hasOwnProperty(k)&&r.hasOwnProperty(d)&&r[k]!==g)x(r),K(u,r[k],r[d]);else if(l!==O&&"function"==typeof b)try{b.call(r,f(P(u,l)),f(P(u,!1)))}catch(C){f(()=>{K(u,!1,C)})()}else{u[k]=l;const C=u[d];if(u[d]=r,u[A]===A&&l===U&&(u[k]=u[q],u[d]=u[H]),l===O&&r instanceof Error){const p=a.currentTask&&a.currentTask.data&&a.currentTask.data.__creationTrace__;p&&h(r,z,{configurable:!0,enumerable:!1,writable:!0,value:p})}for(let p=0;p<C.length;)o(u,C[p++],C[p++],C[p++],C[p++]);if(0==C.length&&l==O){u[k]=0;let p=r;try{throw new Error("Uncaught (in promise): "+function E(u){return u&&u.toString===Object.prototype.toString?(u.constructor&&u.constructor.name||"")+": "+JSON.stringify(u):u?u.toString():Object.prototype.toString.call(u)}(r)+(r&&r.stack?"\n"+r.stack:""))}catch(N){p=N}D&&(p.throwOriginal=!0),p.rejection=r,p.promise=u,p.zone=a.current,p.task=a.currentTask,m.push(p),t.scheduleMicroTask()}}}return u}const te=T("rejectionHandledHandler");function x(u){if(0===u[k]){try{const l=a[te];l&&"function"==typeof l&&l.call(this,{rejection:u[d],promise:u})}catch{}u[k]=O;for(let l=0;l<m.length;l++)u===m[l].promise&&m.splice(l,1)}}function o(u,l,r,f,b){x(u);const C=u[k],p=C?"function"==typeof f?f:Q:"function"==typeof b?b:W;l.scheduleMicroTask("Promise.then",()=>{try{const N=u[d],L=!!r&&A===r[A];L&&(r[H]=N,r[q]=C);const M=l.run(p,void 0,L&&p!==W&&p!==Q?[]:[N]);K(r,!0,M)}catch(N){K(r,!1,N)}},r)}const i=function(){},y=n.AggregateError;class v{static toString(){return"function ZoneAwarePromise() { [native code] }"}static resolve(l){return l instanceof v?l:K(new this(null),U,l)}static reject(l){return K(new this(null),O,l)}static withResolvers(){const l={};return l.promise=new v((r,f)=>{l.resolve=r,l.reject=f}),l}static any(l){if(!l||"function"!=typeof l[Symbol.iterator])return Promise.reject(new y([],"All promises were rejected"));const r=[];let f=0;try{for(let p of l)f++,r.push(v.resolve(p))}catch{return Promise.reject(new y([],"All promises were rejected"))}if(0===f)return Promise.reject(new y([],"All promises were rejected"));let b=!1;const C=[];return new v((p,N)=>{for(let L=0;L<r.length;L++)r[L].then(M=>{b||(b=!0,p(M))},M=>{C.push(M),f--,0===f&&(b=!0,N(new y(C,"All promises were rejected")))})})}static race(l){let r,f,b=new this((N,L)=>{r=N,f=L});function C(N){r(N)}function p(N){f(N)}for(let N of l)J(N)||(N=this.resolve(N)),N.then(C,p);return b}static all(l){return v.allWithCallback(l)}static allSettled(l){return(this&&this.prototype instanceof v?this:v).allWithCallback(l,{thenCallback:f=>({status:"fulfilled",value:f}),errorCallback:f=>({status:"rejected",reason:f})})}static allWithCallback(l,r){let f,b,C=new this((M,G)=>{f=M,b=G}),p=2,N=0;const L=[];for(let M of l){J(M)||(M=this.resolve(M));const G=N;try{M.then(B=>{L[G]=r?r.thenCallback(B):B,p--,0===p&&f(L)},B=>{r?(L[G]=r.errorCallback(B),p--,0===p&&f(L)):b(B)})}catch(B){b(B)}p++,N++}return p-=2,0===p&&f(L),C}constructor(l){const r=this;if(!(r instanceof v))throw new Error("Must be an instanceof Promise.");r[k]=g,r[d]=[];try{const f=S();l&&l(f(P(r,U)),f(P(r,O)))}catch(f){K(r,!1,f)}}get[Symbol.toStringTag](){return"Promise"}get[Symbol.species](){return v}then(l,r){let f=this.constructor?.[Symbol.species];(!f||"function"!=typeof f)&&(f=this.constructor||v);const b=new f(i),C=a.current;return this[k]==g?this[d].push(C,b,l,r):o(this,C,b,l,r),b}catch(l){return this.then(null,l)}finally(l){let r=this.constructor?.[Symbol.species];(!r||"function"!=typeof r)&&(r=v);const f=new r(i);f[A]=A;const b=a.current;return this[k]==g?this[d].push(b,f,l,l):o(this,b,f,l,l),f}}v.resolve=v.resolve,v.reject=v.reject,v.race=v.race,v.all=v.all;const oe=n[_]=n.Promise;n.Promise=v;const se=T("thenPatched");function ie(u){const l=u.prototype,r=c(l,"then");if(r&&(!1===r.writable||!r.configurable))return;const f=l.then;l[w]=f,u.prototype.then=function(b,C){return new v((N,L)=>{f.call(this,N,L)}).then(b,C)},u[se]=!0}return t.patchThen=ie,oe&&(ie(oe),he(n,"fetch",u=>function me(u){return function(l,r){let f=u.apply(l,r);if(f instanceof v)return f;let b=f.constructor;return b[se]||ie(b),f}}(u))),Promise[a.__symbol__("uncaughtPromiseErrors")]=m,v})})(e),function Mt(e){e.__load_patch("toString",n=>{const a=Function.prototype.toString,t=Z("OriginalDelegate"),c=Z("Promise"),h=Z("Error"),E=function(){if("function"==typeof this){const _=this[t];if(_)return"function"==typeof _?a.call(_):Object.prototype.toString.call(_);if(this===Promise){const w=n[c];if(w)return a.call(w)}if(this===Error){const w=n[h];if(w)return a.call(w)}}return a.call(this)};E[t]=a,Function.prototype.toString=E;const T=Object.prototype.toString;Object.prototype.toString=function(){return"function"==typeof Promise&&this instanceof Promise?"[object Promise]":T.call(this)}})}(e),function Zt(e){e.__load_patch("util",(n,a,t)=>{const c=Fe(n);t.patchOnProperties=Ke,t.patchMethod=he,t.bindArguments=Ve,t.patchMacroTask=mt;const h=a.__symbol__("BLACK_LISTED_EVENTS"),E=a.__symbol__("UNPATCHED_EVENTS");n[E]&&(n[h]=n[E]),n[h]&&(a[h]=a[E]=n[h]),t.patchEventPrototype=wt,t.patchEventTarget=Pt,t.isIEOrEdge=vt,t.ObjectDefineProperty=Le,t.ObjectGetOwnPropertyDescriptor=Te,t.ObjectCreate=Et,t.ArraySlice=Tt,t.patchClass=we,t.wrapWithCurrentZone=He,t.filterProperties=it,t.attachOriginToPatched=de,t._redefineProperty=Object.defineProperty,t.patchCallbacks=It,t.getGlobalObjects=()=>({globalSources:tt,zoneSymbolEventNames:ne,eventNames:c,isBrowser:Ge,isMix:Xe,isNode:De,TRUE_STR:ue,FALSE_STR:fe,ZONE_SYMBOL_PREFIX:Pe,ADD_EVENT_LISTENER_STR:Ie,REMOVE_EVENT_LISTENER_STR:Ze})})}(e)})(at),function Nt(e){e.__load_patch("legacy",n=>{const a=n[e.__symbol__("legacyPatch")];a&&a()}),e.__load_patch("timers",n=>{const a="set",t="clear";pe(n,a,t,"Timeout"),pe(n,a,t,"Interval"),pe(n,a,t,"Immediate")}),e.__load_patch("requestAnimationFrame",n=>{pe(n,"request","cancel","AnimationFrame"),pe(n,"mozRequest","mozCancel","AnimationFrame"),pe(n,"webkitRequest","webkitCancel","AnimationFrame")}),e.__load_patch("blocking",(n,a)=>{const t=["alert","prompt","confirm"];for(let c=0;c<t.length;c++)he(n,t[c],(E,T,m)=>function(D,_){return a.current.run(E,n,_,m)})}),e.__load_patch("EventTarget",(n,a,t)=>{(function St(e,n){n.patchEventPrototype(e,n)})(n,t),function Dt(e,n){if(Zone[n.symbol("patchEventTarget")])return;const{eventNames:a,zoneSymbolEventNames:t,TRUE_STR:c,FALSE_STR:h,ZONE_SYMBOL_PREFIX:E}=n.getGlobalObjects();for(let m=0;m<a.length;m++){const D=a[m],I=E+(D+h),j=E+(D+c);t[D]={},t[D][h]=I,t[D][c]=j}const T=e.EventTarget;T&&T.prototype&&n.patchEventTarget(e,n,[T&&T.prototype])}(n,t);const c=n.XMLHttpRequestEventTarget;c&&c.prototype&&t.patchEventTarget(n,t,[c.prototype])}),e.__load_patch("MutationObserver",(n,a,t)=>{we("MutationObserver"),we("WebKitMutationObserver")}),e.__load_patch("IntersectionObserver",(n,a,t)=>{we("IntersectionObserver")}),e.__load_patch("FileReader",(n,a,t)=>{we("FileReader")}),e.__load_patch("on_property",(n,a,t)=>{!function Ot(e,n){if(De&&!Xe||Zone[e.symbol("patchEvents")])return;const a=n.__Zone_ignore_on_properties;let t=[];if(Ge){const c=window;t=t.concat(["Document","SVGElement","Element","HTMLElement","HTMLBodyElement","HTMLMediaElement","HTMLFrameSetElement","HTMLFrameElement","HTMLIFrameElement","HTMLMarqueeElement","Worker"]);const h=function kt(){try{const e=ge.navigator.userAgent;if(-1!==e.indexOf("MSIE ")||-1!==e.indexOf("Trident/"))return!0}catch{}return!1}()?[{target:c,ignoreProperties:["error"]}]:[];ct(c,Fe(c),a&&a.concat(h),Me(c))}t=t.concat(["XMLHttpRequest","XMLHttpRequestEventTarget","IDBIndex","IDBRequest","IDBOpenDBRequest","IDBDatabase","IDBTransaction","IDBCursor","WebSocket"]);for(let c=0;c<t.length;c++){const h=n[t[c]];h&&h.prototype&&ct(h.prototype,Fe(h.prototype),a)}}(t,n)}),e.__load_patch("customElements",(n,a,t)=>{!function Ct(e,n){const{isBrowser:a,isMix:t}=n.getGlobalObjects();(a||t)&&e.customElements&&"customElements"in e&&n.patchCallbacks(n,e.customElements,"customElements","define",["connectedCallback","disconnectedCallback","adoptedCallback","attributeChangedCallback","formAssociatedCallback","formDisabledCallback","formResetCallback","formStateRestoreCallback"])}(n,t)}),e.__load_patch("XHR",(n,a)=>{!function D(_){const w=_.XMLHttpRequest;if(!w)return;const I=w.prototype;let F=I[Ae],J=I[je];if(!F){const R=_.XMLHttpRequestEventTarget;if(R){const P=R.prototype;F=P[Ae],J=P[je]}}const Q="readystatechange",W="scheduled";function k(R){const P=R.data,S=P.target;S[E]=!1,S[m]=!1;const ee=S[h];F||(F=S[Ae],J=S[je]),ee&&J.call(S,Q,ee);const z=S[h]=()=>{if(S.readyState===S.DONE)if(!P.aborted&&S[E]&&R.state===W){const te=S[a.__symbol__("loadfalse")];if(0!==S.status&&te&&te.length>0){const x=R.invoke;R.invoke=function(){const o=S[a.__symbol__("loadfalse")];for(let s=0;s<o.length;s++)o[s]===R&&o.splice(s,1);!P.aborted&&R.state===W&&x.call(R)},te.push(R)}else R.invoke()}else!P.aborted&&!1===S[E]&&(S[m]=!0)};return F.call(S,Q,z),S[t]||(S[t]=R),U.apply(S,P.args),S[E]=!0,R}function d(){}function A(R){const P=R.data;return P.aborted=!0,O.apply(P.target,P.args)}const H=he(I,"open",()=>function(R,P){return R[c]=0==P[2],R[T]=P[1],H.apply(R,P)}),V=Z("fetchTaskAborting"),g=Z("fetchTaskScheduling"),U=he(I,"send",()=>function(R,P){if(!0===a.current[g]||R[c])return U.apply(R,P);{const S={target:R,url:R[T],isPeriodic:!1,args:P,aborted:!1},ee=xe("XMLHttpRequest.send",d,S,k,A);R&&!0===R[m]&&!S.aborted&&ee.state===W&&ee.invoke()}}),O=he(I,"abort",()=>function(R,P){const S=function j(R){return R[t]}(R);if(S&&"string"==typeof S.type){if(null==S.cancelFn||S.data&&S.data.aborted)return;S.zone.cancelTask(S)}else if(!0===a.current[V])return O.apply(R,P)})}(n);const t=Z("xhrTask"),c=Z("xhrSync"),h=Z("xhrListener"),E=Z("xhrScheduled"),T=Z("xhrURL"),m=Z("xhrErrorBeforeScheduled")}),e.__load_patch("geolocation",n=>{n.navigator&&n.navigator.geolocation&&function yt(e,n){const a=e.constructor.name;for(let t=0;t<n.length;t++){const c=n[t],h=e[c];if(h){if(!We(Te(e,c)))continue;e[c]=(T=>{const m=function(){return T.apply(this,Ve(arguments,a+"."+c))};return de(m,T),m})(h)}}}(n.navigator.geolocation,["getCurrentPosition","watchPosition"])}),e.__load_patch("PromiseRejectionEvent",(n,a)=>{function t(c){return function(h){st(n,c).forEach(T=>{const m=n.PromiseRejectionEvent;if(m){const D=new m(c,{promise:h.promise,reason:h.rejection});T.invoke(D)}})}}n.PromiseRejectionEvent&&(a[Z("unhandledPromiseRejectionHandler")]=t("unhandledrejection"),a[Z("rejectionHandledHandler")]=t("rejectionhandled"))}),e.__load_patch("queueMicrotask",(n,a,t)=>{!function Rt(e,n){n.patchMethod(e,"queueMicrotask",a=>function(t,c){Zone.current.scheduleMicroTask("queueMicrotask",c[0])})}(n,t)})}(at)}},$=>{$($.s=8697)}]);