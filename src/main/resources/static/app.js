const state = {exams: [], exam: null, batch: null, task: null, classResults: null, aiSettings: null, pollTimer: null, reviewIndex: -1, createOrigin: 'home', deleteCandidate: null};
const $ = id => document.getElementById(id);
const typeName = type => ({CHOICE:'选择题',FILL_BLANK:'填空题',TRUE_FALSE:'判断题',SHORT_ANSWER:'简答题',PROGRAMMING:'编程题'})[type] || type;
const parseStatusName = value => ({SUCCESS:'解析成功',NEEDS_REVIEW:'需要核对',FAILED:'解析失败'})[value] || value;
const reviewStatusName = value => ({PENDING:'待确认',CONFIRMED:'已确认',IMPORTED:'已导入'})[value] || value;
const taskStatusName = value => ({PENDING:'等待开始',RUNNING:'评分中',COMPLETED:'已完成',PARTIAL_FAILED:'部分失败',PARTIALLY_FAILED:'部分失败',FAILED:'失败',SUCCESS:'成功'})[value] || value;
const money = value => value == null ? '—' : Number(value).toFixed(2);
const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'})[ch]);
const viewIds = ['home-view','create-methods-view','ai-import-view','manual-create-view','exam-library-view','settings-panel','workspace-view'];
const pendingConfirmation = student => student.reviewStatus === 'PENDING';

async function api(url, options = {}) {
    const response = await fetch(url, options);
    const contentType = response.headers.get('content-type') || '';
    let body = '';
    try { body = contentType.includes('json') ? await response.json() : await response.text(); } catch {}
    if (!response.ok) {
        const detail = typeof body === 'object' ? (body.detail || body.message || body.title) : body;
        const error = new Error(detail || `请求失败（HTTP ${response.status}）`); error.status = response.status; throw error;
    }
    return body;
}

function notify(message, kind='info') {
    const node = $('notice'); node.textContent = message; node.className = `notice ${kind}`;
    clearTimeout(node._timer); node._timer = setTimeout(() => node.classList.add('hidden'), 6500);
}
function showView(id) {
    viewIds.forEach(view => $(view).classList.toggle('hidden', view !== id));
    $('workspace-home').classList.toggle('hidden', id !== 'workspace-view'); window.scrollTo({top:0,behavior:'smooth'});
    $('workspace-library').classList.toggle('hidden', id !== 'workspace-view');
}
function examStatus(exam) {
    if (exam.status === 'SCORING') return '已开始批改';
    return exam.standardsReviewed ? '可开始批改' : '评分标准待核对';
}
async function loadExams() {
    state.exams = await api('/api/exams'); renderExamLists();
}
function examRow(exam, management=false) {
    return `<article class="exam-row"><div class="exam-row-main" data-open-exam="${exam.id}" title="${escapeHtml(exam.name)}"><strong>${escapeHtml(exam.name)}</strong><div class="exam-row-meta"><span>${exam.questionCount} 题</span><span>满分 ${money(exam.maxScore)}</span><span class="badge ${exam.standardsReviewed?'success':'warning'}">${examStatus(exam)}</span></div></div><div class="exam-row-actions"><button class="button secondary" data-open-exam="${exam.id}">${management?'打开':'打开'}</button>${management?`<button class="button danger ghost" data-delete-exam="${exam.id}" data-exam-name="${escapeHtml(exam.name)}">删除</button>`:''}</div></article>`;
}
function renderExamLists() {
    const empty = '<div class="empty">还没有试卷，请先创建。</div>';
    const keyword = $('exam-library-search').value.trim().toLocaleLowerCase('zh-CN');
    const visible = keyword ? state.exams.filter(exam => exam.name.toLocaleLowerCase('zh-CN').includes(keyword)) : state.exams;
    $('management-exam-list').innerHTML = visible.length ? visible.map(exam => examRow(exam,true)).join('')
        : (state.exams.length ? '<div class="empty">没有找到名称匹配的试卷。</div>' : empty);
    $('exam-library-count').textContent = keyword ? `找到 ${visible.length} / 共 ${state.exams.length} 份` : `共 ${state.exams.length} 份`;
    document.querySelectorAll('[data-open-exam]').forEach(node => node.onclick = () => openExam(Number(node.dataset.openExam)));
    document.querySelectorAll('[data-delete-exam]').forEach(node => node.onclick = () => openDeleteExamDialog(Number(node.dataset.deleteExam), node.dataset.examName));
}
async function openExam(id, tab='standards') {
    try {
        state.exam = await api(`/api/exams/${id}`); state.batch = null; state.task = null; state.classResults = null;
        showView('workspace-view'); renderExam(); showWorkspaceTab(tab);
    } catch (error) { notify(error.message,'error'); }
}
function openDeleteExamDialog(id, name) {
    state.deleteCandidate = {id, name};
    $('delete-exam-name').textContent = name;
    $('delete-exam-dialog').showModal();
}
function closeDeleteExamDialog() {
    if ($('delete-exam-dialog').open) $('delete-exam-dialog').close();
    state.deleteCandidate = null;
}
async function confirmDeleteExam() {
    if (!state.deleteCandidate) return;
    const candidate = state.deleteCandidate;
    const button = $('confirm-delete-exam');
    button.disabled = true; button.textContent = '正在删除…';
    try {
        await api(`/api/exams/${candidate.id}`,{method:'DELETE'});
        closeDeleteExamDialog();
        await loadExams();
        notify(`试卷“${candidate.name}”及其关联数据已永久删除。`,'success');
    } catch (error) {
        notify(error.message,'error');
    } finally {
        button.disabled = false; button.textContent = '确认删除';
    }
}

function showWorkspaceTab(tab) {
    ['standards','workflow','results'].forEach(name => {
        $(`workspace-${name}`).classList.toggle('hidden', name !== tab);
        document.querySelector(`[data-workspace-tab="${name}"]`).classList.toggle('active', name === tab);
    });
    if (tab === 'workflow') loadWorkflowState();
    if (tab === 'results') loadClassResults();
}
function renderExam() {
    const exam = state.exam; const total = exam.questions.reduce((sum,q) => sum + Number(q.maxScore),0);
    $('exam-title').textContent = exam.name;
    $('exam-summary').innerHTML = `<span class="summary-chip">${exam.questions.length} 道题</span><span class="summary-chip">满分 ${money(total)}</span><span class="summary-chip">${examStatus(exam)}</span>`;
    $('standards-status').textContent = exam.standardsReviewed ? '整卷已确认' : '等待教师核对';
    $('standards-status').className = `badge ${exam.standardsReviewed?'success':'warning'}`;
    $('workflow-standard-badge').textContent = exam.standardsReviewed ? '评分标准已确认' : '需先确认评分标准';
    $('workflow-standard-badge').className = `badge ${exam.standardsReviewed?'success':'warning'}`;
    $('confirm-standards').disabled = exam.standardsReviewed;
    $('confirm-standards').textContent = exam.standardsReviewed ? '评分标准已确认' : '确认整卷评分标准';
    $('begin-grading').disabled = !exam.standardsReviewed;
    $('question-list').innerHTML = exam.questions.map(question => standardCard(question)).join('');
    document.querySelectorAll('[data-standard-question-id]').forEach(card => {
        card.querySelector('.edit-standard').onclick = () => card.querySelector('.standard-editor').classList.toggle('hidden');
        card.querySelector('.cancel-standard').onclick = () => card.querySelector('.standard-editor').classList.add('hidden');
        card.querySelector('.save-standard').onclick = () => saveQuestionStandard(Number(card.dataset.standardQuestionId),card);
    });
}
function standardCard(question) {
    const rubrics = question.rubricItems || [];
    return `<article class="standard-card" data-standard-question-id="${question.id}"><div class="card-heading"><div><strong>第 ${question.questionNo} 题 · ${typeName(question.questionType)}</strong>${question.fillBlankGradingMode?` <span class="badge info">${question.fillBlankGradingMode==='AI'?'AI 辅助':'精确比对'}</span>`:''}</div><div><span class="badge">${money(question.maxScore)} 分</span> <button class="button secondary edit-standard">编辑</button></div></div><div class="standard-read"><div class="standard-value"><strong>题目</strong>${escapeHtml(question.content)}</div><div class="standard-value"><strong>标准答案</strong>${escapeHtml(question.referenceAnswer)}</div>${question.gradingCriteria?`<div class="standard-value"><strong>评分细则</strong>${escapeHtml(question.gradingCriteria)}</div>`:''}${rubrics.length?`<div class="standard-value"><strong>结构化评分点</strong>${rubrics.map(r=>`${escapeHtml(r.name)}（${money(r.maxScore)} 分）`).join('；')}</div>`:''}</div><div class="standard-editor hidden"><div class="form-grid"><label class="field">题目满分<input class="standard-max" type="number" min="0.01" step="0.01" value="${question.maxScore}"></label></div><label class="field">标准答案<textarea class="standard-reference" rows="3">${escapeHtml(question.referenceAnswer)}</textarea></label><label class="field">评分细则<textarea class="standard-criteria" rows="3">${escapeHtml(question.gradingCriteria||'')}</textarea></label>${rubrics.length?`<label class="field">结构化评分点<textarea class="standard-rubrics" rows="${Math.max(3,rubrics.length)}">${rubrics.map(r=>`${escapeHtml(r.name)}|${r.maxScore}`).join('\n')}</textarea><small>每行：评分点名称|分值</small></label>`:''}<div class="actions end"><button class="button ghost cancel-standard">取消</button><button class="button primary save-standard">保存本题</button></div></div></article>`;
}
function parseRubrics(text,maxScore) {
    const items = text.split(/\r?\n/).map(v=>v.trim()).filter(Boolean).map((line,index)=>{const split=line.lastIndexOf('|');if(split<1)throw new Error('评分点格式应为“名称|分值”');const name=line.slice(0,split).trim(),score=Number(line.slice(split+1));if(!name||!Number.isFinite(score)||score<=0)throw new Error('评分点名称不能为空，分值必须大于 0');return {itemOrder:index+1,name,maxScore:score};});
    if(items.reduce((s,i)=>s+i.maxScore,0)>maxScore+0.000001)throw new Error('结构化评分点总分不得超过题目满分'); return items;
}
async function saveQuestionStandard(questionId,card) {
    try {
        const referenceAnswer=card.querySelector('.standard-reference').value.trim(),maxScore=Number(card.querySelector('.standard-max').value);
        if(!referenceAnswer||!Number.isFinite(maxScore)||maxScore<=0)throw new Error('标准答案不能为空，题目满分必须大于 0');
        const editor=card.querySelector('.standard-rubrics'); const rubricItems=editor?parseRubrics(editor.value,maxScore):[];
        state.exam=await api(`/api/exams/${state.exam.id}/questions/${questionId}/standards`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({referenceAnswer,maxScore,gradingCriteria:card.querySelector('.standard-criteria').value.trim()||null,rubricItems})});
        renderExam(); await loadExams(); notify('本题已保存，整卷确认状态已撤销。','success');
    } catch(error){notify(error.message,'error');}
}

function resetEditor() { $('exam-name').value=''; $('question-editor').innerHTML=''; $('draft-issues').innerHTML=''; $('draft-guidance').textContent='保存后仍需确认整卷评分标准，才能开始阅卷。'; }
function addQuestion(defaults={}) {
    const fragment=$('question-template').content.cloneNode(true), card=fragment.querySelector('.question-edit-card');
    card.querySelector('.q-type').value=defaults.questionType||'CHOICE'; card.querySelector('.q-max').value=defaults.maxScore||2; card.querySelector('.q-content').value=defaults.content||''; card.querySelector('.q-reference').value=defaults.referenceAnswer||''; card.querySelector('.q-criteria').value=defaults.gradingCriteria||''; card.querySelector('.q-fill-mode').value=defaults.fillBlankGradingMode||'EXACT'; card.querySelector('.q-rubrics').value=(defaults.rubricItems||[]).map(r=>`${r.name}|${r.maxScore}`).join('\n');
    if(defaults.needsReview){card.querySelector('.draft-flag').textContent='待核对';card.title=(defaults.reviewNotes||[]).join('；');}
    card.querySelector('.q-type').onchange=()=>updateQuestionEditor(card); card.querySelector('.remove-question').onclick=()=>{card.remove();renumberQuestions();}; $('question-editor').appendChild(fragment); updateQuestionEditor(card); renumberQuestions();
}
function updateQuestionEditor(card){card.querySelector('.fill-mode-wrap').classList.toggle('hidden',card.querySelector('.q-type').value!=='FILL_BLANK');}
function renumberQuestions(){[...document.querySelectorAll('.question-edit-card')].forEach((card,index)=>card.querySelector('.question-index').textContent=`第 ${index+1} 题`);}
function collectExamRequest(){
    const name=$('exam-name').value.trim(),cards=[...document.querySelectorAll('.question-edit-card')]; if(!name||!cards.length)throw new Error('请填写考试名称并至少添加一道题');
    return {name,questions:cards.map((card,index)=>{const questionType=card.querySelector('.q-type').value,content=card.querySelector('.q-content').value.trim(),referenceAnswer=card.querySelector('.q-reference').value.trim(),maxScore=Number(card.querySelector('.q-max').value);if(!content||!referenceAnswer||!Number.isFinite(maxScore)||maxScore<=0)throw new Error(`第 ${index+1} 题的题干、标准答案和有效满分均为必填项`);const rubricItems=parseRubrics(card.querySelector('.q-rubrics').value,maxScore);if((questionType==='SHORT_ANSWER'||(questionType==='FILL_BLANK'&&card.querySelector('.q-fill-mode').value==='AI'))&&!rubricItems.length)throw new Error(`第 ${index+1} 题使用 AI 评分，必须填写结构化评分点`);return {questionNo:index+1,questionType,content,maxScore,referenceAnswer,gradingCriteria:card.querySelector('.q-criteria').value.trim()||null,fillBlankGradingMode:questionType==='FILL_BLANK'?card.querySelector('.q-fill-mode').value:null,rubricItems};})};
}
async function createExam(event){event.preventDefault();try{const created=await api('/api/exams',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(collectExamRequest())});await loadExams();resetEditor();notify('试卷已保存，请继续核对评分标准。','success');await openExam(created.id);}catch(error){notify(error.message,'error');}}
async function importExamDocx(){
    const examFile=$('exam-docx-file').files[0]; if(!examFile){notify('请选择考试试卷 DOCX。','error');return;} const button=$('preview-exam-docx');button.disabled=true;button.textContent='AI 正在解析…';
    try{const form=new FormData();form.append('examFile',examFile);const ref=$('reference-docx-file').files[0];if(ref)form.append('referenceFile',ref);const draft=await api('/api/exam-import/preview',{method:'POST',body:form});resetEditor();$('exam-name').value=draft.suggestedName||examFile.name.replace(/\.docx$/i,'');draft.questions.forEach(addQuestion);$('draft-guidance').textContent='这是 AI 生成的可编辑草稿。待核对标记不会自动确认，保存后还需整卷确认。';const issues=[...(draft.issues||[]),...draft.questions.flatMap(q=>(q.reviewNotes||[]).map(note=>`第 ${q.questionNo} 题：${note}`))];$('draft-issues').innerHTML=issues.length?`<div class="issues"><strong>需要教师核对</strong><ul>${issues.map(i=>`<li>${escapeHtml(i)}</li>`).join('')}</ul></div>`:'';$('exam-import-result').innerHTML=`<div class="inline-status">已提取 ${draft.questions.length} 道题，${draft.needsReview?'存在待核对内容':'仍请逐题复核'}。草稿尚未写入数据库。</div>`;showView('manual-create-view');notify('试卷草稿已生成，请逐题核对后保存。','success');}catch(error){$('exam-import-result').innerHTML=`<div class="issues"><strong>未创建试卷</strong><p>${escapeHtml(error.message)}</p></div>`;notify(error.message,'error');}finally{button.disabled=false;button.textContent='解析并生成草稿';}
}

async function loadWorkflowState(){
    if(!state.exam)return; try{state.batch=await api(`/api/exams/${state.exam.id}/answer-import/batches/latest`);renderBatch();}catch(error){if(error.status!==404)notify(error.message,'error');else{$('batch-summary').innerHTML='';$('batch-students').innerHTML='';$('workflow-action').classList.add('hidden');}}
    try{state.task=await api(`/api/exams/${state.exam.id}/grading-tasks/latest`);renderTask();if(state.task.status==='RUNNING'||state.task.status==='PENDING')scheduleTaskPoll();}catch(error){if(error.status!==404)notify(error.message,'error');}
}
async function uploadZip(){const file=$('zip-file').files[0];if(!file){notify('请选择 ZIP 文件。','error');return;}const button=$('upload-zip');button.disabled=true;button.textContent='正在解析…';try{const form=new FormData();form.append('file',file);state.batch=await api(`/api/exams/${state.exam.id}/answer-import/batches/preview`,{method:'POST',body:form});renderBatch();notify('答卷解析完成，请处理异常后开始评分。','success');}catch(error){notify(error.message,'error');}finally{button.disabled=false;button.textContent='上传并解析';}}
function renderBatch(){
    const batch=state.batch;if(!batch)return;const pending=batch.students.filter(pendingConfirmation);const recoverable=batch.students.filter(s=>s.reviewStatus==='CONFIRMED');const unresolved=[...pending.filter(s=>s.parseStatus!=='SUCCESS'),...recoverable];const normal=pending.filter(s=>s.parseStatus==='SUCCESS');
    $('batch-summary').innerHTML=`<div class="metric-row"><div class="metric"><span>学生</span><strong>${batch.uploadedStudentCount}</strong></div><div class="metric"><span>正常</span><strong>${batch.parseSuccessCount}</strong></div><div class="metric"><span>待处理异常</span><strong>${unresolved.length}</strong></div><div class="metric"><span>已导入</span><strong>${batch.importedCount}</strong></div></div>`;
    const abnormalHtml=unresolved.map(s=>studentCard(s,true)).join('');const normalHtml=normal.length?`<details class="student-card"><summary>${normal.length} 份正常答卷（点击查看详情）</summary><div>${normal.map(s=>studentCard(s,false,true)).join('')}</div></details>`:'';const imported=batch.students.filter(s=>s.reviewStatus==='IMPORTED');const importedHtml=imported.length?`<details class="student-card"><summary>${imported.length} 份已导入答卷</summary>${imported.map(s=>`<p>${escapeHtml(s.studentNo)} · ${escapeHtml(s.studentName)} · submissionId ${s.submissionId}</p>`).join('')}</details>`:'';
    $('batch-students').innerHTML=(abnormalHtml||normalHtml||importedHtml)?`${abnormalHtml}${normalHtml}${importedHtml}`:'<div class="empty">暂无待处理答卷。</div>';
    document.querySelectorAll('[data-save-student]').forEach(button=>button.onclick=()=>saveStudentCorrection(Number(button.dataset.saveStudent)));
    $('workflow-action').classList.toggle('hidden',batch.uploadedStudentCount===0);
    $('confirm-import-grade').disabled=!state.exam.standardsReviewed||unresolved.length>0;
    $('confirm-import-grade').textContent=unresolved.length?`还有 ${unresolved.length} 份异常待处理`:'确认并开始评分';
}
function studentCard(student,editable,nested=false){
    const issues=(student.issues||[]).map(i=>`<li>${escapeHtml(i.message)}</li>`).join('');const answers=student.answers.map(a=>editable?`<div class="answer-row"><label class="field">对应题目<select class="answer-question" data-answer-id="${a.id}">${state.exam.questions.map(q=>`<option value="${q.id}" ${q.id===a.questionId?'selected':''}>第 ${q.questionNo} 题 · ${typeName(q.questionType)}</option>`).join('')}</select></label><label class="field">答案原文<textarea class="answer-text" data-answer-id="${a.id}">${escapeHtml(a.rawAnswer)}</textarea></label></div>`:`<div class="answer-row"><strong>第 ${a.questionNo||a.sourceQuestionNo||'?'} 题 · ${typeName(a.questionType||a.sourceQuestionType)}</strong><div class="standard-value">${escapeHtml(a.rawAnswer)}</div></div>`).join('');
    const body=`${editable?`<div class="form-grid"><label class="field">学号<input class="student-no" value="${escapeHtml(student.studentNo||student.detectedStudentNo||'')}"></label><label class="field">姓名<input class="student-name" value="${escapeHtml(student.studentName||student.detectedStudentName||'')}"></label></div>`:`<p>${escapeHtml(student.studentNo)} · ${escapeHtml(student.studentName)}</p>`}${issues?`<div class="issues"><strong>发现的问题</strong><ul>${issues}</ul></div>`:''}<div>${answers}</div>${editable?`<div class="actions end"><button class="button primary" data-save-student="${student.id}">保存修正</button></div>`:''}`;
    if(nested)return `<div class="standard-value">${body}</div>`;return `<details class="student-card problem" data-student-card="${student.id}" open><summary>${escapeHtml(student.studentNo||student.detectedStudentNo||'未知学号')} · ${escapeHtml(student.studentName||student.detectedStudentName||'未知姓名')} · ${parseStatusName(student.parseStatus)}</summary>${body}</details>`;
}
async function saveStudentCorrection(studentId){
    const student=state.batch.students.find(s=>s.id===studentId),card=document.querySelector(`[data-student-card="${studentId}"]`);try{const answers=student.answers.map(a=>({answerId:a.id,questionId:Number(card.querySelector(`.answer-question[data-answer-id="${a.id}"]`).value),answerText:card.querySelector(`.answer-text[data-answer-id="${a.id}"]`).value}));await api(`/api/answer-import/batches/${state.batch.id}/students/${studentId}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify({studentNo:card.querySelector('.student-no').value.trim(),studentName:card.querySelector('.student-name').value.trim(),expectedVersion:student.version,answers})});state.batch=await api(`/api/answer-import/batches/${state.batch.id}`);renderBatch();notify('异常答卷已修正，可以继续。','success');}catch(error){notify(error.message,'error');}
}
async function confirmImportAndGrade(){const button=$('confirm-import-grade');button.disabled=true;button.textContent='正在导入并启动评分…';try{const result=await api(`/api/answer-import/batches/${state.batch.id}/confirm-import-and-grade`,{method:'POST'});state.task=result.gradingTask;state.batch=await api(`/api/answer-import/batches/${state.batch.id}`);renderBatch();renderTask();scheduleTaskPoll();if(result.importResult.failures.length)notify(`评分已启动，但有 ${result.importResult.failures.length} 份答卷导入失败，可修正后重试。`,'error');else notify('答卷已导入，建议评分已启动。','success');}catch(error){notify(`${error.message}。修正问题后可再次点击，已完成的步骤不会重复。`,'error');}finally{button.disabled=false;button.textContent='确认并开始评分';}}
function renderTask(){const task=state.task;if(!task)return;const percent=task.totalCount?Math.round(task.processedCount/task.totalCount*100):0;const failures=(task.items||[]).filter(i=>i.status==='FAILED');$('task-view').innerHTML=`<div class="task-card"><div class="card-heading"><strong>建议评分 · ${taskStatusName(task.status)}</strong><span>${task.processedCount}/${task.totalCount}</span></div><div class="progress"><span style="width:${percent}%"></span></div><div class="summary-line"><span class="summary-chip">成功 ${task.successCount}</span><span class="summary-chip">失败 ${task.failedCount}</span></div>${failures.length?`<div class="issues"><strong>失败答卷</strong><ul>${failures.map(i=>`<li>${escapeHtml(i.studentNo)} ${escapeHtml(i.studentName)}：${escapeHtml(i.errorMessage||'评分失败')}</li>`).join('')}</ul></div>`:''}</div>`;$('retry-grading').classList.toggle('hidden',!failures.length);$('view-results').classList.toggle('hidden',task.status==='RUNNING'||task.status==='PENDING');}
function scheduleTaskPoll(){clearTimeout(state.pollTimer);state.pollTimer=setTimeout(async()=>{try{state.task=await api(`/api/grading/tasks/${state.task.id}`);renderTask();if(state.task.status==='RUNNING'||state.task.status==='PENDING')scheduleTaskPoll();}catch(error){notify(error.message,'error');}},1200);}
async function retryTask(){try{state.task=await api(`/api/grading/tasks/${state.task.id}/retry-failed`,{method:'POST'});renderTask();scheduleTaskPoll();notify('已重试失败答卷。','success');}catch(error){notify(error.message,'error');}}

async function loadClassResults(){if(!state.exam)return;try{state.classResults=await api(`/api/exams/${state.exam.id}/class-results`);renderClassResults();}catch(error){notify(error.message,'error');}}
function suggestionText(submission){const qmap=new Map(state.classResults.questions.map(q=>[q.questionId,q]));let score=0,covered=0,total=0,complete=true;state.classResults.questions.forEach(q=>total+=Number(q.maxScore));const byId=new Map(submission.questionScores.map(q=>[q.questionId,q]));state.classResults.questions.forEach(q=>{const item=byId.get(q.questionId);if(item&&item.suggestedScore!=null){score+=Number(item.suggestedScore);covered+=Number(q.maxScore);}else complete=false;});return complete?`建议 ${money(score)} / ${money(total)}`:`已建议 ${money(score)} / ${money(covered)}，另有 ${money(total-covered)} 分待人工评分`;}
function renderClassResults(){
    const data=state.classResults,complete=data.submissions.filter(s=>s.completionStatus==='COMPLETE').length;$('class-summary').innerHTML=`<span class="summary-chip">学生 ${data.submissions.length}</span><span class="summary-chip">已完成审核 ${complete}</span><span class="summary-chip">待完成 ${data.submissions.length-complete}</span>`;
    $('class-results').innerHTML=data.submissions.length?`<table><thead><tr><th>学号</th><th>姓名</th><th>建议评分</th><th>审核状态</th><th>最终成绩</th></tr></thead><tbody>${data.submissions.map((s,index)=>`<tr class="clickable" data-review-index="${index}"><td>${escapeHtml(s.studentNo)}</td><td>${escapeHtml(s.studentName)}</td><td><div class="suggestion-note">${suggestionText(s)}</div></td><td>${s.confirmedQuestionCount}/${s.questionCount} ${s.completionStatus==='COMPLETE'?'<span class="badge success">已完成</span>':'<span class="badge warning">待审核</span>'}</td><td><strong>${s.finalScore==null?'未完成':money(s.finalScore)}</strong></td></tr>`).join('')}</tbody></table>`:'<div class="empty">尚无正式导入的答卷。</div>';
    document.querySelectorAll('[data-review-index]').forEach(row=>row.onclick=()=>openReview(Number(row.dataset.reviewIndex)));
}
async function openReview(index){const student=state.classResults.submissions[index];state.reviewIndex=index;try{const [results,summary]=await Promise.all([api(`/api/submissions/${student.submissionId}/results`),api(`/api/submissions/${student.submissionId}/summary`)]);$('results-list-view').classList.add('hidden');$('review-detail-view').classList.remove('hidden');$('review-title').textContent=`${student.studentNo} · ${student.studentName}`;$('review-summary').innerHTML=`<div class="summary-line"><span class="summary-chip">已审核 ${summary.confirmedQuestionCount}/${summary.questionCount}</span><span class="summary-chip">当前确认分 ${money(summary.confirmedScore)}</span><span class="summary-chip">最终成绩 ${summary.finalScore==null?'未完成':money(summary.finalScore)}</span></div>`;renderReviewResults(results);$('review-next').disabled=index>=state.classResults.submissions.length-1;}catch(error){notify(error.message,'error');}}
function renderReviewResults(results){const sorted=[...results].sort((a,b)=>{const ap=a.reviewStatus==='CONFIRMED'?1:0,bp=b.reviewStatus==='CONFIRMED'?1:0;return ap-bp||a.questionNo-b.questionNo;});$('review-results').innerHTML=sorted.map(result=>`<details class="review-card ${result.reviewStatus==='CONFIRMED'?'':'pending'}" data-result-id="${result.id}" ${result.reviewStatus==='CONFIRMED'?'':'open'}><summary>第 ${result.questionNo} 题 · ${typeName(result.questionType)} · ${result.reviewStatus==='CONFIRMED'?`已确认 ${money(result.actualScore)} 分`:'待审核'}</summary><div class="standard-value"><strong>题目</strong>${escapeHtml(result.question)}</div><div class="standard-value"><strong>学生答案</strong>${escapeHtml(result.studentAnswer)}</div><div class="standard-value"><strong>标准答案</strong>${escapeHtml(result.referenceAnswer)}</div><div class="standard-value"><strong>建议评分</strong>${result.suggestedScore==null?'无有效建议分':`${money(result.suggestedScore)} / ${money(result.maxScore)}`}</div><div class="standard-value"><strong>评分理由</strong>${escapeHtml(result.reason||result.failureMessage||'自动评分未生成理由，需教师人工评分。')}</div>${(result.criterionScores||[]).length?`<div class="standard-value"><strong>评分点建议</strong>${result.criterionScores.map(i=>`${escapeHtml(i.criterion)}：${money(i.suggestedScore)}/${money(i.maxScore)}，${escapeHtml(i.reason)}`).join('<br>')}</div>`:''}<div class="score-controls"><label class="field">实际分（满分 ${money(result.maxScore)}）<input class="actual-score" type="number" min="0" max="${result.maxScore}" step="0.01" value="${result.actualScore??result.suggestedScore??''}"></label>${result.suggestedScore!=null?`<button class="button secondary accept-score" data-result-id="${result.id}">接受建议分</button>`:''}<button class="button primary set-score" data-result-id="${result.id}">${result.reviewStatus==='CONFIRMED'?'修改实际分':'确认实际分'}</button></div></details>`).join('');document.querySelectorAll('.accept-score').forEach(b=>b.onclick=()=>reviewScore(Number(b.dataset.resultId),'ACCEPT_SUGGESTION'));document.querySelectorAll('.set-score').forEach(b=>b.onclick=()=>reviewScore(Number(b.dataset.resultId),'SET_SCORE'));state.currentReviewResults=results;}
async function reviewScore(resultId,action){const result=state.currentReviewResults.find(r=>r.id===resultId),card=document.querySelector(`[data-result-id="${resultId}"]`);const payload={action,expectedVersion:result.version};if(action==='SET_SCORE'){const raw=card.querySelector('.actual-score').value.trim();if(!raw){notify('请填写实际分。','error');return;}payload.actualScore=Number(raw);if(!Number.isFinite(payload.actualScore)){notify('实际分格式不正确。','error');return;}}try{await api(`/api/grading/results/${resultId}/review`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});await loadClassResults();await openReview(state.reviewIndex);notify('实际分已保存。','success');}catch(error){notify(error.message,'error');}}

function showExamLibrary(){showView('exam-library-view');loadExams().catch(error=>notify(error.message,'error'));}
function showSettings(){showView('settings-panel');loadAiSettings();}
async function loadAiSettings(){try{state.aiSettings=await api('/api/settings/ai');$('ai-base-url').value=state.aiSettings.baseUrl||'';$('ai-model').value=state.aiSettings.model||'';$('ai-api-key').value='';$('ai-config-badge').textContent=state.aiSettings.configured?'已配置':'未完成配置';$('ai-config-badge').className=`badge ${state.aiSettings.configured?'success':'warning'}`;$('ai-config-summary').textContent=`当前来源：${state.aiSettings.source==='SAVED_LOCAL'?'网页保存配置':'环境变量'}。${state.aiSettings.statusMessage||''}`;}catch(error){notify(error.message,'error');}}
function aiRequest(){return {apiKey:$('ai-api-key').value.trim()||null,baseUrl:$('ai-base-url').value.trim(),model:$('ai-model').value.trim()};}
async function saveAiSettings(){try{state.aiSettings=await api('/api/settings/ai',{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(aiRequest())});$('ai-api-key').value='';await loadAiSettings();notify('AI 配置已保存，新评分请求立即使用该配置。','success');}catch(error){notify(error.message,'error');}}
async function testAiConnection(){if(!confirm('测试连接会向 AI 服务发送一次最小请求，可能产生少量费用。是否继续？'))return;const button=$('test-ai-connection');button.disabled=true;button.textContent='正在测试…';try{const result=await api('/api/settings/ai/test',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(aiRequest())});notify(result.message,'success');}catch(error){notify(error.message,'error');}finally{button.disabled=false;button.textContent='测试连接';}}

function goHome(){clearTimeout(state.pollTimer);showView('home-view');loadExams().catch(e=>notify(e.message,'error'));}
function showCreateMethods(origin='home'){state.createOrigin=origin;resetEditor();$('create-methods-back').textContent=origin==='library'?'← 返回试卷库':'← 返回首页';showView('create-methods-view');}
function showManualCreate(){resetEditor();addQuestion();showView('manual-create-view');}

$('home-button').onclick=goHome;$('workspace-home').onclick=goHome;$('workspace-library').onclick=showExamLibrary;$('show-settings').onclick=showSettings;$('show-create-methods').onclick=()=>showCreateMethods('home');$('show-exam-library').onclick=showExamLibrary;$('create-methods-back').onclick=()=>state.createOrigin==='library'?showExamLibrary():goHome();document.querySelectorAll('[data-go-home]').forEach(b=>b.onclick=goHome);document.querySelectorAll('[data-go-create]').forEach(b=>b.onclick=()=>showCreateMethods(state.createOrigin));
$('show-ai-import').onclick=()=>showView('ai-import-view');$('show-manual-create').onclick=showManualCreate;$('preview-exam-docx').onclick=importExamDocx;$('add-question').onclick=()=>addQuestion();$('exam-form').onsubmit=createExam;$('management-create').onclick=()=>showCreateMethods('library');
document.querySelectorAll('[data-workspace-tab]').forEach(b=>b.onclick=()=>showWorkspaceTab(b.dataset.workspaceTab));
$('exam-library-search').oninput=renderExamLists;
$('confirm-standards').onclick=async()=>{try{state.exam=await api(`/api/exams/${state.exam.id}/standards/confirm`,{method:'PUT'});renderExam();await loadExams();notify('评分标准已确认，可以开始批改。','success');}catch(error){notify(error.message,'error');}};
$('begin-grading').onclick=()=>showWorkspaceTab('workflow');$('upload-zip').onclick=uploadZip;$('confirm-import-grade').onclick=confirmImportAndGrade;$('retry-grading').onclick=retryTask;$('view-results').onclick=()=>showWorkspaceTab('results');$('refresh-results').onclick=loadClassResults;$('back-to-students').onclick=()=>{$('review-detail-view').classList.add('hidden');$('results-list-view').classList.remove('hidden');};$('review-next').onclick=()=>openReview(state.reviewIndex+1);$('save-ai-settings').onclick=saveAiSettings;$('test-ai-connection').onclick=testAiConnection;
$('cancel-delete-exam').onclick=closeDeleteExamDialog;$('confirm-delete-exam').onclick=confirmDeleteExam;$('delete-exam-dialog').addEventListener('close',()=>{state.deleteCandidate=null;});

loadExams().catch(error=>notify(error.message,'error'));
