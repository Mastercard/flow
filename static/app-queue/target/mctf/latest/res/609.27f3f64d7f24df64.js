"use strict";(self.webpackChunkreport=self.webpackChunkreport||[]).push([[609],{2609:(Wt,D,I)=>{I.r(D),I.d(D,{diagram:()=>Ht});var v=I(9037),R=I(8586),gt=I(5847),mt=I(1223),xt=I(8674),J=(I(1764),I(6780),I(2735),function(){var n=function(g,i,r,c){for(r=r||{},c=g.length;c--;r[g[c]]=i);return r},t=[6,8,10,11,12,14,16,17,20,21],e=[1,9],a=[1,10],s=[1,11],h=[1,12],l=[1,13],p=[1,16],y=[1,17],f={trace:function(){},yy:{},symbols_:{error:2,start:3,timeline:4,document:5,EOF:6,line:7,SPACE:8,statement:9,NEWLINE:10,title:11,acc_title:12,acc_title_value:13,acc_descr:14,acc_descr_value:15,acc_descr_multiline_value:16,section:17,period_statement:18,event_statement:19,period:20,event:21,$accept:0,$end:1},terminals_:{2:"error",4:"timeline",6:"EOF",8:"SPACE",10:"NEWLINE",11:"title",12:"acc_title",13:"acc_title_value",14:"acc_descr",15:"acc_descr_value",16:"acc_descr_multiline_value",17:"section",20:"period",21:"event"},productions_:[0,[3,3],[5,0],[5,2],[7,2],[7,1],[7,1],[7,1],[9,1],[9,2],[9,2],[9,1],[9,1],[9,1],[9,1],[18,1],[19,1]],performAction:function(i,r,c,d,u,o,P){var x=o.length-1;switch(u){case 1:return o[x-1];case 2:case 6:case 7:this.$=[];break;case 3:o[x-1].push(o[x]),this.$=o[x-1];break;case 4:case 5:this.$=o[x];break;case 8:d.getCommonDb().setDiagramTitle(o[x].substr(6)),this.$=o[x].substr(6);break;case 9:this.$=o[x].trim(),d.getCommonDb().setAccTitle(this.$);break;case 10:case 11:this.$=o[x].trim(),d.getCommonDb().setAccDescription(this.$);break;case 12:d.addSection(o[x].substr(8)),this.$=o[x].substr(8);break;case 15:d.addTask(o[x],0,""),this.$=o[x];break;case 16:d.addEvent(o[x].substr(2)),this.$=o[x]}},table:[{3:1,4:[1,2]},{1:[3]},n(t,[2,2],{5:3}),{6:[1,4],7:5,8:[1,6],9:7,10:[1,8],11:e,12:a,14:s,16:h,17:l,18:14,19:15,20:p,21:y},n(t,[2,7],{1:[2,1]}),n(t,[2,3]),{9:18,11:e,12:a,14:s,16:h,17:l,18:14,19:15,20:p,21:y},n(t,[2,5]),n(t,[2,6]),n(t,[2,8]),{13:[1,19]},{15:[1,20]},n(t,[2,11]),n(t,[2,12]),n(t,[2,13]),n(t,[2,14]),n(t,[2,15]),n(t,[2,16]),n(t,[2,4]),n(t,[2,9]),n(t,[2,10])],defaultActions:{},parseError:function(i,r){if(!r.recoverable){var c=new Error(i);throw c.hash=r,c}this.trace(i)},parse:function(i){var r=this,c=[0],d=[],u=[null],o=[],P=this.table,x="",M=0,V=0,B=2,C=1,U=o.slice.call(arguments,1),k=Object.create(this.lexer),E={yy:{}};for(var w in this.yy)Object.prototype.hasOwnProperty.call(this.yy,w)&&(E.yy[w]=this.yy[w]);k.setInput(i,E.yy),E.yy.lexer=k,E.yy.parser=this,typeof k.yylloc>"u"&&(k.yylloc={});var T=k.yylloc;o.push(T);var O=k.options&&k.options.ranges;function z(){var N;return"number"!=typeof(N=d.pop()||k.lex()||C)&&(N instanceof Array&&(N=(d=N).pop()),N=r.symbols_[N]||N),N}this.parseError="function"==typeof E.yy.parseError?E.yy.parseError:Object.getPrototypeOf(this).parseError;for(var b,A,S,X,Z,L,G,F={};;){if(this.defaultActions[A=c[c.length-1]]?S=this.defaultActions[A]:((null===b||typeof b>"u")&&(b=z()),S=P[A]&&P[A][b]),typeof S>"u"||!S.length||!S[0]){var q="";for(Z in G=[],P[A])this.terminals_[Z]&&Z>B&&G.push("'"+this.terminals_[Z]+"'");q=k.showPosition?"Parse error on line "+(M+1)+":\n"+k.showPosition()+"\nExpecting "+G.join(", ")+", got '"+(this.terminals_[b]||b)+"'":"Parse error on line "+(M+1)+": Unexpected "+(b==C?"end of input":"'"+(this.terminals_[b]||b)+"'"),this.parseError(q,{text:k.match,token:this.terminals_[b]||b,line:k.yylineno,loc:T,expected:G})}if(S[0]instanceof Array&&S.length>1)throw new Error("Parse Error: multiple actions possible at state: "+A+", token: "+b);switch(S[0]){case 1:c.push(b),u.push(k.yytext),o.push(k.yylloc),c.push(S[1]),b=null,V=k.yyleng,x=k.yytext,M=k.yylineno,T=k.yylloc;break;case 2:if(F.$=u[u.length-(L=this.productions_[S[1]][1])],F._$={first_line:o[o.length-(L||1)].first_line,last_line:o[o.length-1].last_line,first_column:o[o.length-(L||1)].first_column,last_column:o[o.length-1].last_column},O&&(F._$.range=[o[o.length-(L||1)].range[0],o[o.length-1].range[1]]),typeof(X=this.performAction.apply(F,[x,V,M,E.yy,S[1],u,o].concat(U)))<"u")return X;L&&(c=c.slice(0,-1*L*2),u=u.slice(0,-1*L),o=o.slice(0,-1*L)),c.push(this.productions_[S[1]][0]),u.push(F.$),o.push(F._$),c.push(P[c[c.length-2]][c[c.length-1]]);break;case 3:return!0}}return!0}};function m(){this.yy={}}return f.lexer={EOF:1,parseError:function(r,c){if(!this.yy.parser)throw new Error(r);this.yy.parser.parseError(r,c)},setInput:function(i,r){return this.yy=r||this.yy||{},this._input=i,this._more=this._backtrack=this.done=!1,this.yylineno=this.yyleng=0,this.yytext=this.matched=this.match="",this.conditionStack=["INITIAL"],this.yylloc={first_line:1,first_column:0,last_line:1,last_column:0},this.options.ranges&&(this.yylloc.range=[0,0]),this.offset=0,this},input:function(){var i=this._input[0];return this.yytext+=i,this.yyleng++,this.offset++,this.match+=i,this.matched+=i,i.match(/(?:\r\n?|\n).*/g)?(this.yylineno++,this.yylloc.last_line++):this.yylloc.last_column++,this.options.ranges&&this.yylloc.range[1]++,this._input=this._input.slice(1),i},unput:function(i){var r=i.length,c=i.split(/(?:\r\n?|\n)/g);this._input=i+this._input,this.yytext=this.yytext.substr(0,this.yytext.length-r),this.offset-=r;var d=this.match.split(/(?:\r\n?|\n)/g);this.match=this.match.substr(0,this.match.length-1),this.matched=this.matched.substr(0,this.matched.length-1),c.length-1&&(this.yylineno-=c.length-1);var u=this.yylloc.range;return this.yylloc={first_line:this.yylloc.first_line,last_line:this.yylineno+1,first_column:this.yylloc.first_column,last_column:c?(c.length===d.length?this.yylloc.first_column:0)+d[d.length-c.length].length-c[0].length:this.yylloc.first_column-r},this.options.ranges&&(this.yylloc.range=[u[0],u[0]+this.yyleng-r]),this.yyleng=this.yytext.length,this},more:function(){return this._more=!0,this},reject:function(){return this.options.backtrack_lexer?(this._backtrack=!0,this):this.parseError("Lexical error on line "+(this.yylineno+1)+". You can only invoke reject() in the lexer when the lexer is of the backtracking persuasion (options.backtrack_lexer = true).\n"+this.showPosition(),{text:"",token:null,line:this.yylineno})},less:function(i){this.unput(this.match.slice(i))},pastInput:function(){var i=this.matched.substr(0,this.matched.length-this.match.length);return(i.length>20?"...":"")+i.substr(-20).replace(/\n/g,"")},upcomingInput:function(){var i=this.match;return i.length<20&&(i+=this._input.substr(0,20-i.length)),(i.substr(0,20)+(i.length>20?"...":"")).replace(/\n/g,"")},showPosition:function(){var i=this.pastInput(),r=new Array(i.length+1).join("-");return i+this.upcomingInput()+"\n"+r+"^"},test_match:function(i,r){var c,d,u;if(this.options.backtrack_lexer&&(u={yylineno:this.yylineno,yylloc:{first_line:this.yylloc.first_line,last_line:this.last_line,first_column:this.yylloc.first_column,last_column:this.yylloc.last_column},yytext:this.yytext,match:this.match,matches:this.matches,matched:this.matched,yyleng:this.yyleng,offset:this.offset,_more:this._more,_input:this._input,yy:this.yy,conditionStack:this.conditionStack.slice(0),done:this.done},this.options.ranges&&(u.yylloc.range=this.yylloc.range.slice(0))),(d=i[0].match(/(?:\r\n?|\n).*/g))&&(this.yylineno+=d.length),this.yylloc={first_line:this.yylloc.last_line,last_line:this.yylineno+1,first_column:this.yylloc.last_column,last_column:d?d[d.length-1].length-d[d.length-1].match(/\r?\n?/)[0].length:this.yylloc.last_column+i[0].length},this.yytext+=i[0],this.match+=i[0],this.matches=i,this.yyleng=this.yytext.length,this.options.ranges&&(this.yylloc.range=[this.offset,this.offset+=this.yyleng]),this._more=!1,this._backtrack=!1,this._input=this._input.slice(i[0].length),this.matched+=i[0],c=this.performAction.call(this,this.yy,this,r,this.conditionStack[this.conditionStack.length-1]),this.done&&this._input&&(this.done=!1),c)return c;if(this._backtrack){for(var o in u)this[o]=u[o];return!1}return!1},next:function(){if(this.done)return this.EOF;var i,r,c,d;this._input||(this.done=!0),this._more||(this.yytext="",this.match="");for(var u=this._currentRules(),o=0;o<u.length;o++)if((c=this._input.match(this.rules[u[o]]))&&(!r||c[0].length>r[0].length)){if(r=c,d=o,this.options.backtrack_lexer){if(!1!==(i=this.test_match(c,u[o])))return i;if(this._backtrack){r=!1;continue}return!1}if(!this.options.flex)break}return r?!1!==(i=this.test_match(r,u[d]))&&i:""===this._input?this.EOF:this.parseError("Lexical error on line "+(this.yylineno+1)+". Unrecognized text.\n"+this.showPosition(),{text:"",token:null,line:this.yylineno})},lex:function(){return this.next()||this.lex()},begin:function(r){this.conditionStack.push(r)},popState:function(){return this.conditionStack.length-1>0?this.conditionStack.pop():this.conditionStack[0]},_currentRules:function(){return this.conditionStack.length&&this.conditionStack[this.conditionStack.length-1]?this.conditions[this.conditionStack[this.conditionStack.length-1]].rules:this.conditions.INITIAL.rules},topState:function(r){return(r=this.conditionStack.length-1-Math.abs(r||0))>=0?this.conditionStack[r]:"INITIAL"},pushState:function(r){this.begin(r)},stateStackSize:function(){return this.conditionStack.length},options:{"case-insensitive":!0},performAction:function(r,c,d,u){switch(d){case 0:case 1:case 3:case 4:break;case 2:return 10;case 5:return 4;case 6:return 11;case 7:return this.begin("acc_title"),12;case 8:return this.popState(),"acc_title_value";case 9:return this.begin("acc_descr"),14;case 10:return this.popState(),"acc_descr_value";case 11:this.begin("acc_descr_multiline");break;case 12:this.popState();break;case 13:return"acc_descr_multiline_value";case 14:return 17;case 15:return 21;case 16:return 20;case 17:return 6;case 18:return"INVALID"}},rules:[/^(?:%(?!\{)[^\n]*)/i,/^(?:[^\}]%%[^\n]*)/i,/^(?:[\n]+)/i,/^(?:\s+)/i,/^(?:#[^\n]*)/i,/^(?:timeline\b)/i,/^(?:title\s[^#\n;]+)/i,/^(?:accTitle\s*:\s*)/i,/^(?:(?!\n||)*[^\n]*)/i,/^(?:accDescr\s*:\s*)/i,/^(?:(?!\n||)*[^\n]*)/i,/^(?:accDescr\s*\{\s*)/i,/^(?:[\}])/i,/^(?:[^\}]*)/i,/^(?:section\s[^#:\n;]+)/i,/^(?::\s[^#:\n;]+)/i,/^(?:[^#:\n;]+)/i,/^(?:$)/i,/^(?:.)/i],conditions:{acc_descr_multiline:{rules:[12,13],inclusive:!1},acc_descr:{rules:[10],inclusive:!1},acc_title:{rules:[8],inclusive:!1},INITIAL:{rules:[0,1,2,3,4,5,6,7,9,11,14,15,16,17,18],inclusive:!0}}},m.prototype=f,f.Parser=m,new m}());J.parser=J;const _t=J;let H="",tt=0;const Q=[],j=[],W=[],et=()=>v.L,nt=function(){Q.length=0,j.length=0,H="",W.length=0,(0,v.v)()},st=function(n){H=n,Q.push(n)},it=function(){return Q},rt=function(){let n=ct(),e=0;for(;!n&&e<100;)n=ct(),e++;return j.push(...W),j},at=function(n,t,e){const a={id:tt++,section:H,type:H,task:n,score:t||0,events:e?[e]:[]};W.push(a)},lt=function(n){W.find(e=>e.id===tt-1).events.push(n)},ot=function(n){j.push({section:H,type:H,description:n,task:n,classes:[]})},ct=function(){const n=function(e){return W[e].processed};let t=!0;for(const[e,a]of W.entries())n(e),t=t&&a.processed;return t},bt=Object.freeze(Object.defineProperty({__proto__:null,addEvent:lt,addSection:st,addTask:at,addTaskOrg:ot,clear:nt,default:{clear:nt,getCommonDb:et,addSection:st,getSections:it,getTasks:rt,addTask:at,addTaskOrg:ot,addEvent:lt},getCommonDb:et,getSections:it,getTasks:rt},Symbol.toStringTag,{value:"Module"}));function pt(n,t){n.each(function(){var s,e=(0,R.Ys)(this),a=e.text().split(/(\s+|<br>)/).reverse(),h=[],p=e.attr("y"),y=parseFloat(e.attr("dy")),f=e.text(null).append("tspan").attr("x",0).attr("y",p).attr("dy",y+"em");for(let _=0;_<a.length;_++)h.push(s=a[a.length-1-_]),f.text(h.join(" ").trim()),(f.node().getComputedTextLength()>t||"<br>"===s)&&(h.pop(),f.text(h.join(" ").trim()),h="<br>"===s?[""]:[s],f=e.append("tspan").attr("x",0).attr("y",p).attr("dy","1.1em").text(s))})}const $_drawNode=function(n,t,e,a){const s=e%12-1,h=n.append("g");t.section=s,h.attr("class",(t.class?t.class+" ":"")+"timeline-node section-"+s);const l=h.append("g"),p=h.append("g"),f=p.append("text").text(t.descr).attr("dy","1em").attr("alignment-baseline","middle").attr("dominant-baseline","middle").attr("text-anchor","middle").call(pt,t.width).node().getBBox(),_=a.fontSize&&a.fontSize.replace?a.fontSize.replace("px",""):a.fontSize;return t.height=f.height+1.1*_*.5+t.padding,t.height=Math.max(t.height,t.maxHeight),t.width=t.width+2*t.padding,p.attr("transform","translate("+t.width/2+", "+t.padding/2+")"),function(n,t,e){n.append("path").attr("id","node-"+t.id).attr("class","node-bkg node-"+t.type).attr("d",`M0 ${t.height-5} v${10-t.height} q0,-5 5,-5 h${t.width-10} q5,0 5,5 v${t.height-5} H0 Z`),n.append("line").attr("class","node-line-"+e).attr("x1",0).attr("y1",t.height).attr("x2",t.width).attr("y2",t.height)}(l,t,s),t},$_getVirtualNodeHeight=function(n,t,e){const a=n.append("g"),h=a.append("text").text(t.descr).attr("dy","1em").attr("alignment-baseline","middle").attr("dominant-baseline","middle").attr("text-anchor","middle").call(pt,t.width).node().getBBox(),l=e.fontSize&&e.fontSize.replace?e.fontSize.replace("px",""):e.fontSize;return a.remove(),h.height+1.1*l*.5+t.padding},yt=function(n,t,e,a,s,h,l,p,y,f,_){var m;for(const g of t){const i={descr:g.task,section:e,number:e,width:150,padding:20,maxHeight:h};v.l.debug("taskNode",i);const r=n.append("g").attr("class","taskWrapper"),d=$_drawNode(r,i,e,l).height;if(v.l.debug("taskHeight after draw",d),r.attr("transform",`translate(${a}, ${s})`),h=Math.max(h,d),g.events){const u=n.append("g").attr("class","lineWrapper");let o=h;o+=Ct(n,g.events,e,a,s+=100,l),s-=100,u.append("line").attr("x1",a+95).attr("y1",s+h).attr("x2",a+95).attr("y2",s+h+(_?h:f)+y+120).attr("stroke-width",2).attr("stroke","black").attr("marker-end","url(#arrowhead)").attr("stroke-dasharray","5,5")}a+=200,_&&(null==(m=l.timeline)||!m.disableMulticolor)&&e++}s-=10},Ct=function(n,t,e,a,s,h){let l=0;const p=s;s+=100;for(const y of t){const f={descr:y,section:e,number:e,width:150,padding:20,maxHeight:50};v.l.debug("eventNode",f);const _=n.append("g").attr("class","eventWrapper"),g=$_drawNode(_,f,e,h).height;l+=g,_.attr("transform",`translate(${a}, ${s})`),s=s+10+g}return s=p,l},Ht={db:bt,renderer:{setConf:()=>{},draw:function(n,t,e,a){var s,h;const l=(0,v.c)(),p=l.leftMargin??50;v.l.debug("timeline",a.db);const y=l.securityLevel;let f;"sandbox"===y&&(f=(0,R.Ys)("#i"+t));const m=(0,R.Ys)("sandbox"===y?f.nodes()[0].contentDocument.body:"body").select("#"+t);m.append("g");const g=a.db.getTasks(),i=a.db.getCommonDb().getDiagramTitle();v.l.debug("task",g),function(n){n.append("defs").append("marker").attr("id","arrowhead").attr("refX",5).attr("refY",2).attr("markerWidth",6).attr("markerHeight",4).attr("orient","auto").append("path").attr("d","M 0,0 V 4 L6,2 Z")}(m);const r=a.db.getSections();v.l.debug("sections",r);let c=0,d=0,u=0,o=0,P=50+p,x=50;o=50;let M=0,V=!0;r.forEach(function(E){const T=$_getVirtualNodeHeight(m,{number:M,descr:E,section:M,width:150,padding:20,maxHeight:c},l);v.l.debug("sectionHeight before draw",T),c=Math.max(c,T+20)});let B=0,C=0;v.l.debug("tasks.length",g.length);for(const[E,w]of g.entries()){const O=$_getVirtualNodeHeight(m,{number:E,descr:w,section:w.section,width:150,padding:20,maxHeight:d},l);v.l.debug("taskHeight before draw",O),d=Math.max(d,O+20),B=Math.max(B,w.events.length);let z=0;for(let b=0;b<w.events.length;b++)z+=$_getVirtualNodeHeight(m,{descr:w.events[b],section:w.section,number:w.section,width:150,padding:20,maxHeight:50},l);C=Math.max(C,z)}v.l.debug("maxSectionHeight before draw",c),v.l.debug("maxTaskHeight before draw",d),r&&r.length>0?r.forEach(E=>{const w=g.filter(b=>b.section===E),T={number:M,descr:E,section:M,width:200*Math.max(w.length,1)-50,padding:20,maxHeight:c};v.l.debug("sectionNode",T);const O=m.append("g"),z=$_drawNode(O,T,M,l);v.l.debug("sectionNode output",z),O.attr("transform",`translate(${P}, 50)`),x+=c+50,w.length>0&&yt(m,w,M,P,x,d,l,0,C,c,!1),P+=200*Math.max(w.length,1),x=50,M++}):(V=!1,yt(m,g,M,P,x,d,l,0,C,c,!0));const U=m.node().getBBox();v.l.debug("bounds",U),i&&m.append("text").text(i).attr("x",U.width/2-p).attr("font-size","4ex").attr("font-weight","bold").attr("y",20),u=V?c+d+150:d+100,m.append("g").attr("class","lineWrapper").append("line").attr("x1",p).attr("y1",u).attr("x2",U.width+3*p).attr("y2",u).attr("stroke-width",4).attr("stroke","black").attr("marker-end","url(#arrowhead)"),(0,v.o)(void 0,m,(null==(s=l.timeline)?void 0:s.padding)??50,(null==(h=l.timeline)?void 0:h.useMaxWidth)??!1)}},parser:_t,styles:n=>`\n  .edge {\n    stroke-width: 3;\n  }\n  ${(n=>{let t="";for(let e=0;e<n.THEME_COLOR_LIMIT;e++)n["lineColor"+e]=n["lineColor"+e]||n["cScaleInv"+e],n["lineColor"+e]=(0,gt.Z)(n["lineColor"+e])?(0,mt.Z)(n["lineColor"+e],20):(0,xt.Z)(n["lineColor"+e],20);for(let e=0;e<n.THEME_COLOR_LIMIT;e++)t+=`\n    .section-${e-1} rect, .section-${e-1} path, .section-${e-1} circle, .section-${e-1} path  {\n      fill: ${n["cScale"+e]};\n    }\n    .section-${e-1} text {\n     fill: ${n["cScaleLabel"+e]};\n    }\n    .node-icon-${e-1} {\n      font-size: 40px;\n      color: ${n["cScaleLabel"+e]};\n    }\n    .section-edge-${e-1}{\n      stroke: ${n["cScale"+e]};\n    }\n    .edge-depth-${e-1}{\n      stroke-width: ${""+(17-3*e)};\n    }\n    .section-${e-1} line {\n      stroke: ${n["cScaleInv"+e]} ;\n      stroke-width: 3;\n    }\n\n    .lineWrapper line{\n      stroke: ${n["cScaleLabel"+e]} ;\n    }\n\n    .disabled, .disabled circle, .disabled text {\n      fill: lightgray;\n    }\n    .disabled text {\n      fill: #efefef;\n    }\n    `;return t})(n)}\n  .section-root rect, .section-root path, .section-root circle  {\n    fill: ${n.git0};\n  }\n  .section-root text {\n    fill: ${n.gitBranchLabel0};\n  }\n  .icon-container {\n    height:100%;\n    display: flex;\n    justify-content: center;\n    align-items: center;\n  }\n  .edge {\n    fill: none;\n  }\n  .eventWrapper  {\n   filter: brightness(120%);\n  }\n`}}}]);