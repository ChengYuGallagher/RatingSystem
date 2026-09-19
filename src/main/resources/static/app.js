const state = { exams: [], exam: null, batch: null, task: null, classResults: null, pollTimer: null };

const $ = id => document.getElementById(id);
const typeName = type => ({CHOICE:'选择题', FILL_BLANK:'填空题', TRUE_FALSE:'判断题', SHORT_ANSWER:'简答题', PROGRAMMING:'编程题'})[type] || type;
const money = value => value == null ? '—' : Number(value).toFixed(2);
const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'})[ch]);

async function api(url, options = {}) {
    const response = await fetch(url, options);
    const contentType = response.headers.get('content-type') || '';
    const body = contentType.includes('json') ? await response.json() : await response.text();
    if (!response.ok) {
        const error = new Error(body.detail || body.message || body || `请求失败：${response.status}`);
        error.status = response.status;
        throw error;
    }
    return body;
}

function notify(message, kind = 'info') {
    const notice = $('notice');
    notice.textContent = message;
    notice.className = `notice ${kind}`;
    clearTimeout(notice._timer);
    notice._timer = setTimeout(() => notice.classList.add('hidden'), 5000);
}

async function loadExams(selectId) {
    try {
        state.exams = await api('/api/exams');
        renderExamList();
        if (selectId) await selectExam(selectId);
    } catch (error) { notify(error.message, 'error'); }
}

function renderExamList() {
    $('exam-list').innerHTML = state.exams.length ? state.exams.map(exam => `
        <button class="exam-item ${state.exam?.id === exam.id ? 'active' : ''}" data-exam-id="${exam.id}">
            <strong>${escapeHtml(exam.name)}</strong>
            <span>${exam.questionCount} 题 · ${money(exam.maxScore)} 分 · ${exam.standardsReviewed ? '标准已确认' : '待核对标准'}</span>
        </button>`).join('') : '<p class="score-pending">暂无评分方案</p>';
    document.querySelectorAll('[data-exam-id]').forEach(button => button.onclick = () => selectExam(Number(button.dataset.examId)));
}

async function selectExam(id) {
    try {
        state.exam = await api(`/api/exams/${id}`);
        state.batch = null; state.task = null; state.classResults = null;
        $('empty-state').classList.add('hidden');
        $('create-exam-panel').classList.add('hidden');
        $('exam-workspace').classList.remove('hidden');
        renderExamList(); renderExam(); showTab('standards');
    } catch (error) { notify(error.message, 'error'); }
}

function renderExam() {
    const exam = state.exam;
    $('exam-title').textContent = exam.name;
    const total = exam.questions.reduce((sum, q) => sum + Number(q.maxScore), 0);
    $('exam-summary').innerHTML = `<div class="metric"><span>题目数量</span><strong>${exam.questions.length}</strong></div>
        <div class="metric"><span>试卷满分</span><strong>${money(total)}</strong></div>
        <div class="metric"><span>当前状态</span><strong>${exam.status}</strong></div>`;
    $('standards-status').textContent = exam.standardsReviewed ? '已由教师确认' : '等待教师核对';
    $('standards-status').className = `badge ${exam.standardsReviewed ? 'success' : 'warning'}`;
    $('confirm-standards').disabled = exam.standardsReviewed;
    $('confirm-standards').textContent = exam.standardsReviewed ? '评分标准已确认' : '确认评分标准';
    $('start-grading').disabled = !exam.standardsReviewed;
    $('question-list').innerHTML = exam.questions.map(q => `
        <article class="question-card" data-standard-question-id="${q.id}">
            <div class="question-head"><strong>第 ${q.questionNo} 题 · ${typeName(q.questionType)}</strong>
                <div class="question-meta">${q.fillBlankGradingMode ? `<span>${q.fillBlankGradingMode}</span>` : ''}</div></div>
            <div class="answer-box"><strong>题干</strong><br>${escapeHtml(q.content)}</div>
            <div class="form-grid"><label class="field">满分<input class="standard-max" type="number" min="0.01" step="0.01" value="${q.maxScore}"></label></div>
            <label class="field">标准答案<textarea class="standard-reference" rows="2">${escapeHtml(q.referenceAnswer)}</textarea></label>
            <label class="field">评分细则<textarea class="standard-criteria" rows="2">${escapeHtml(q.gradingCriteria || '')}</textarea></label>
            ${q.rubricItems.length ? `<label class="field">结构化评分点<textarea class="standard-rubrics" rows="${Math.max(3, q.rubricItems.length)}">${q.rubricItems.map(r => `${escapeHtml(r.name)}|${r.maxScore}`).join('\n')}</textarea><small>每行格式：评分点名称|分值；评分点总分不得超过题目满分。</small></label>` : ''}
            <div class="actions"><button class="button secondary small save-standard">保存本题评分标准</button></div>
        </article>`).join('');
    document.querySelectorAll('[data-standard-question-id]').forEach(card => {
        card.querySelector('.save-standard').onclick = () => saveQuestionStandard(Number(card.dataset.standardQuestionId), card);
    });
}

async function saveQuestionStandard(questionId, card) {
    const referenceAnswer = card.querySelector('.standard-reference').value.trim();
    const maxScore = Number(card.querySelector('.standard-max').value);
    if (!referenceAnswer || !Number.isFinite(maxScore) || maxScore <= 0) {
        notify('标准答案不能为空，题目满分必须大于 0。', 'error');
        return;
    }
    let rubricItems = [];
    const rubricEditor = card.querySelector('.standard-rubrics');
    if (rubricEditor) {
        try {
            rubricItems = rubricEditor.value.split(/\r?\n/).map(line => line.trim()).filter(Boolean).map((line, index) => {
                const split = line.lastIndexOf('|');
                if (split < 1) throw new Error('评分点格式应为“名称|分值”');
                const score = Number(line.slice(split + 1).trim());
                if (!Number.isFinite(score) || score <= 0) throw new Error('评分点分值必须大于 0');
                return {itemOrder:index + 1, name:line.slice(0, split).trim(), maxScore:score};
            });
        } catch (error) {
            notify(error.message, 'error');
            return;
        }
    }
    try {
        state.exam = await api(`/api/exams/${state.exam.id}/questions/${questionId}/standards`, {
            method:'PUT', headers:{'Content-Type':'application/json'},
            body:JSON.stringify({referenceAnswer, maxScore, gradingCriteria:card.querySelector('.standard-criteria').value.trim() || null, rubricItems})
        });
        renderExam();
        state.exams = await api('/api/exams');
        renderExamList();
        notify('本题评分标准已保存，整份试卷需要重新确认后才能批量评分。', 'success');
    } catch (error) { notify(error.message, 'error'); }
}

function showCreateExam() {
    $('empty-state').classList.add('hidden'); $('exam-workspace').classList.add('hidden');
    $('create-exam-panel').classList.remove('hidden');
    if (!$('question-editor').children.length) addQuestion();
}

function addQuestion(defaults = {}) {
    const fragment = $('question-template').content.cloneNode(true);
    const card = fragment.querySelector('.question-edit-card');
    card.querySelector('.q-type').value = defaults.questionType || 'CHOICE';
    card.querySelector('.q-max').value = defaults.maxScore || 2;
    card.querySelector('.q-content').value = defaults.content || '';
    card.querySelector('.q-reference').value = defaults.referenceAnswer || '';
    card.querySelector('.q-criteria').value = defaults.gradingCriteria || '';
    card.querySelector('.q-type').onchange = () => updateQuestionEditor(card);
    card.querySelector('.remove-question').onclick = () => { card.remove(); renumberQuestions(); };
    $('question-editor').appendChild(fragment); updateQuestionEditor(card); renumberQuestions();
}

function updateQuestionEditor(card) {
    card.querySelector('.fill-mode-wrap').classList.toggle('hidden', card.querySelector('.q-type').value !== 'FILL_BLANK');
}

function renumberQuestions() {
    [...document.querySelectorAll('.question-edit-card')].forEach((card, index) => card.querySelector('.question-index').textContent = `第 ${index + 1} 题`);
}

function collectExamRequest() {
    const name = $('exam-name').value.trim();
    const cards = [...document.querySelectorAll('.question-edit-card')];
    if (!name || !cards.length) throw new Error('请填写考试名称并至少添加一道题');
    return { name, questions: cards.map((card, index) => {
        const type = card.querySelector('.q-type').value;
        const rubricLines = card.querySelector('.q-rubrics').value.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
        const rubricItems = rubricLines.map((line, itemIndex) => {
            const split = line.lastIndexOf('|');
            if (split < 1) throw new Error(`第 ${index + 1} 题评分点格式应为“名称|分值”`);
            return { itemOrder: itemIndex + 1, name: line.slice(0, split).trim(), maxScore: Number(line.slice(split + 1).trim()) };
        });
        return {
            questionNo: index + 1, questionType: type,
            content: card.querySelector('.q-content').value.trim(),
            maxScore: Number(card.querySelector('.q-max').value),
            referenceAnswer: card.querySelector('.q-reference').value.trim(),
            gradingCriteria: card.querySelector('.q-criteria').value.trim() || null,
            fillBlankGradingMode: type === 'FILL_BLANK' ? card.querySelector('.q-fill-mode').value : null,
            rubricItems
        };
    }) };
}

async function createExam() {
    try {
        const created = await api('/api/exams', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(collectExamRequest()) });
        $('exam-name').value = ''; $('question-editor').innerHTML = '';
        notify('评分方案已创建，请继续核对并确认评分标准。', 'success');
        await loadExams(created.id);
    } catch (error) { notify(error.message, 'error'); }
}

async function confirmStandards() {
    if (!confirm('确认题干、标准答案、满分和评分细则均已核对？')) return;
    try { state.exam = await api(`/api/exams/${state.exam.id}/standards/confirm`, {method:'PUT'}); renderExam(); await loadExams(state.exam.id); notify('评分标准已确认，可以启动批量评分。', 'success'); }
    catch (error) { notify(error.message, 'error'); }
}

async function uploadZip() {
    const file = $('zip-file').files[0];
    if (!file) return notify('请选择 ZIP 文件', 'error');
    const form = new FormData(); form.append('file', file);
    try {
        notify('正在上传并解析，请稍候…');
        state.batch = await api(`/api/exams/${state.exam.id}/answer-import/batches/preview`, {method:'POST', body:form});
        renderBatch(); notify('ZIP 解析完成，请核对学生和答案。', 'success');
    } catch (error) { notify(error.message, 'error'); }
}

function renderBatch() {
    const b = state.batch;
    const pendingConfirmation = b.students.filter(student => student.reviewStatus === 'PENDING').length;
    $('batch-summary').innerHTML = `<div class="metric-row"><div class="metric"><span>上传学生</span><strong>${b.uploadedStudentCount}</strong></div>
        <div class="metric"><span>解析成功</span><strong>${b.parseSuccessCount}</strong></div><div class="metric"><span>解析需核对</span><strong>${b.needsReviewCount}</strong></div>
        <div class="metric"><span>待确认</span><strong>${pendingConfirmation}</strong></div>
        <div class="metric"><span>解析失败</span><strong>${b.parseFailedCount}</strong></div><div class="metric"><span>已确认</span><strong>${b.confirmedCount}</strong></div>
        <div class="metric"><span>已导入</span><strong>${b.importedCount}</strong></div></div>`;
    $('batch-students').innerHTML = b.students.map(student => `
        <article class="student-card" data-student-card="${student.id}">
            <div class="student-head"><div><strong>${escapeHtml(student.studentNo || '未识别')} · ${escapeHtml(student.studentName || '未识别')}</strong>
                <div class="question-meta">${escapeHtml(student.sourcePath)} · 识别 ${student.recognizedQuestionCount}/${student.expectedQuestionCount}</div></div>
                <span class="badge ${student.reviewStatus === 'IMPORTED' ? 'success' : student.parseStatus === 'FAILED' ? 'error' : 'warning'}">${student.reviewStatus === 'PENDING' ? student.parseStatus : student.reviewStatus}</span></div>
            ${student.issues.length ? `<ul class="issue-list">${student.issues.map(i => `<li>${escapeHtml(i.message)}</li>`).join('')}</ul>` : ''}
            <div class="form-grid"><label class="field">学号<input class="student-no" value="${escapeHtml(student.studentNo || '')}"></label>
                <label class="field">姓名<input class="student-name" value="${escapeHtml(student.studentName || '')}"></label></div>
            <div class="student-answers">${student.answers.map(answer => answerEditor(answer)).join('')}</div>
            <div class="actions">
                <button class="button secondary small save-student" ${student.reviewStatus !== 'PENDING' ? 'disabled' : ''}>保存修正</button>
                <button class="button success small confirm-student" ${student.reviewStatus !== 'PENDING' ? 'disabled' : ''}>确认该答卷</button>
            </div>
        </article>`).join('');
    document.querySelectorAll('[data-student-card]').forEach(card => {
        const id = Number(card.dataset.studentCard);
        card.querySelector('.save-student').onclick = () => saveStudent(id, card);
        card.querySelector('.confirm-student').onclick = () => confirmStudent(id);
    });
    $('import-confirmed').classList.toggle('hidden', b.confirmedCount === 0);
}

function answerEditor(answer) {
    const options = state.exam.questions.map(q => `<option value="${q.id}" ${q.id === answer.questionId ? 'selected' : ''}>第 ${q.questionNo} 题 · ${typeName(q.questionType)}</option>`).join('');
    return `<div class="answer-edit" data-answer-id="${answer.id}"><label class="field">题目映射<select class="answer-question">${options}</select></label>
        <label class="field">原始答案<textarea class="answer-text" rows="3">${escapeHtml(answer.rawAnswer)}</textarea></label></div>`;
}

async function saveStudent(studentId, card) {
    const student = state.batch.students.find(s => s.id === studentId);
    const answers = [...card.querySelectorAll('[data-answer-id]')].map(row => ({answerId:Number(row.dataset.answerId), questionId:Number(row.querySelector('.answer-question').value), answerText:row.querySelector('.answer-text').value}));
    try {
        await api(`/api/answer-import/batches/${state.batch.id}/students/${studentId}`, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({studentNo:card.querySelector('.student-no').value.trim(), studentName:card.querySelector('.student-name').value.trim(), expectedVersion:student.version, answers})});
        await reloadBatch(); notify('修正已保存。', 'success');
    } catch (error) { notify(error.message, 'error'); }
}

async function confirmStudent(studentId) {
    const student = state.batch.students.find(s => s.id === studentId);
    try { await api(`/api/answer-import/batches/${state.batch.id}/students/${studentId}/confirm`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify({expectedVersion:student.version})}); await reloadBatch(); notify('答卷已确认。', 'success'); }
    catch (error) { notify(error.message, 'error'); }
}

async function reloadBatch() { state.batch = await api(`/api/answer-import/batches/${state.batch.id}`); renderBatch(); }

async function loadLatestBatch() {
    if (!state.exam) return;
    try {
        state.batch = await api(`/api/exams/${state.exam.id}/answer-import/batches/latest`);
        renderBatch();
    } catch (error) {
        if (error.status !== 404) notify(error.message, 'error');
    }
}

async function importConfirmed() {
    try {
        const result = await api(`/api/answer-import/batches/${state.batch.id}/import`, {method:'POST'});
        await reloadBatch();
        notify(`本次导入 ${result.importedCount} 份，剩余待导入 ${result.remainingConfirmedCount} 份。`, result.failures.length ? 'error' : 'success');
    } catch (error) { notify(error.message, 'error'); }
}

async function startGrading() {
    try {
        state.task = await api(`/api/exams/${state.exam.id}/grading-tasks`, {method:'POST'});
        renderTask(); beginPolling();
    } catch (error) { notify(error.message, 'error'); }
}

async function retryGrading() {
    try { state.task = await api(`/api/grading/tasks/${state.task.id}/retry-failed`, {method:'POST'}); renderTask(); beginPolling(); }
    catch (error) { notify(error.message, 'error'); }
}

function beginPolling() {
    clearInterval(state.pollTimer);
    if (!state.task || state.task.status !== 'RUNNING') return;
    state.pollTimer = setInterval(async () => {
        try { state.task = await api(`/api/grading/tasks/${state.task.id}`); renderTask(); if (state.task.status !== 'RUNNING') { clearInterval(state.pollTimer); await loadClassResults(); } }
        catch (error) { clearInterval(state.pollTimer); notify(error.message, 'error'); }
    }, 1000);
}

function renderTask() {
    const t = state.task;
    if (!t) { $('task-view').innerHTML = '<p class="score-pending">尚未启动批量评分。</p>'; return; }
    const percent = t.totalCount ? Math.round(t.processedCount * 100 / t.totalCount) : 0;
    $('task-view').innerHTML = `<div class="metric-row"><div class="metric"><span>任务状态</span><strong>${t.status}</strong></div><div class="metric"><span>进度</span><strong>${t.processedCount}/${t.totalCount}</strong></div><div class="metric"><span>成功</span><strong>${t.successCount}</strong></div><div class="metric"><span>失败</span><strong>${t.failedCount}</strong></div></div>
        <div class="progress"><div style="width:${percent}%"></div></div>
        <table><thead><tr><th>学号</th><th>姓名</th><th>状态</th><th>尝试次数</th><th>错误</th></tr></thead><tbody>${t.items.map(i => `<tr><td>${escapeHtml(i.studentNo)}</td><td>${escapeHtml(i.studentName)}</td><td>${i.status}</td><td>${i.attemptCount}</td><td>${escapeHtml(i.errorMessage || '')}</td></tr>`).join('')}</tbody></table>`;
    $('retry-grading').classList.toggle('hidden', t.failedCount === 0 || t.status === 'RUNNING');
}

async function loadClassResults() {
    if (!state.exam) return;
    try { state.classResults = await api(`/api/exams/${state.exam.id}/class-results`); renderClassResults(); }
    catch (error) { notify(error.message, 'error'); }
}

async function loadLatestTask() {
    if (!state.exam) return;
    try {
        state.task = await api(`/api/exams/${state.exam.id}/grading-tasks/latest`);
        renderTask();
        beginPolling();
    } catch (error) {
        if (error.status !== 404) notify(error.message, 'error');
    }
}

function renderClassResults() {
    const data = state.classResults;
    if (!data || !data.submissions.length) { $('class-results').innerHTML = '<p class="score-pending">尚无正式导入的答卷。</p>'; return; }
    $('class-results').innerHTML = `<table><thead><tr><th>学号</th><th>姓名</th><th>审核进度</th>${data.questions.map(q => `<th>第${q.questionNo}题<br>${money(q.maxScore)}</th>`).join('')}<th>最终总分</th></tr></thead><tbody>
        ${data.submissions.map(s => `<tr class="clickable" data-submission-id="${s.submissionId}"><td>${escapeHtml(s.studentNo)}</td><td>${escapeHtml(s.studentName)}</td><td>${s.confirmedQuestionCount}/${s.questionCount}</td>
            ${s.questionScores.map(score => `<td class="${score.actualScore != null ? 'score-confirmed' : score.suggestedScore != null ? 'score-suggested' : 'score-pending'}">${score.actualScore != null ? money(score.actualScore) : score.suggestedScore != null ? `建议 ${money(score.suggestedScore)}` : '待评分'}</td>`).join('')}
            <td class="${s.finalScore != null ? 'score-confirmed' : 'score-pending'}">${s.finalScore != null ? money(s.finalScore) : '未完成'}</td></tr>`).join('')}</tbody></table>`;
    document.querySelectorAll('[data-submission-id]').forEach(row => row.onclick = () => openReview(Number(row.dataset.submissionId)));
}

async function openReview(submissionId) {
    try {
        const [results, summary] = await Promise.all([api(`/api/submissions/${submissionId}/results`), api(`/api/submissions/${submissionId}/summary`)]);
        $('review-drawer').classList.remove('hidden');
        $('review-title').textContent = `${summary.studentNo} · ${summary.studentName} · 逐题审核`;
        $('review-summary').innerHTML = `<div class="metric-row"><div class="metric"><span>已确认</span><strong>${summary.confirmedQuestionCount}/${summary.questionCount}</strong></div><div class="metric"><span>当前确认分</span><strong>${money(summary.confirmedScore)}</strong></div><div class="metric"><span>最终总分</span><strong>${summary.finalScore == null ? '未完成' : money(summary.finalScore)}</strong></div></div>`;
        $('review-results').innerHTML = results.map(renderReviewCard).join('') || '<p class="score-pending">该答卷尚未生成评分记录。</p>';
        document.querySelectorAll('[data-result-id]').forEach(card => bindReviewActions(card, results.find(r => r.id === Number(card.dataset.resultId)), submissionId));
        $('review-drawer').scrollIntoView({behavior:'smooth'});
    } catch (error) { notify(error.message, 'error'); }
}

function renderReviewCard(result) {
    const programming = result.questionType === 'PROGRAMMING';
    return `<article class="review-card" data-result-id="${result.id}"><div class="review-head"><strong>第 ${result.questionNo} 题 · ${typeName(result.questionType)}</strong><span class="badge ${result.reviewStatus === 'CONFIRMED' ? 'success' : result.gradingStatus === 'FAILED' ? 'error' : 'warning'}">${result.reviewStatus === 'CONFIRMED' ? '已确认' : result.gradingStatus}</span></div>
        ${programming ? '<div class="callout"><strong>编程题未实际运行代码</strong><p>当前系统只保存文字答案，请教师阅读后人工给分。</p></div>' : ''}
        <div class="review-grid"><div><div class="answer-box"><strong>题目</strong><br>${escapeHtml(result.question)}</div><div class="answer-box"><strong>标准答案</strong><br>${escapeHtml(result.referenceAnswer)}</div></div><div><pre class="answer-box"><strong>学生答案</strong>\n${escapeHtml(result.studentAnswer)}</pre><div class="answer-box"><strong>评分理由</strong><br>${escapeHtml(result.reason || result.failureMessage || '暂无')}</div></div></div>
        ${result.criterionScores.length ? `<ul class="rubrics">${result.criterionScores.map(c => `<li>${escapeHtml(c.criterion)}：建议 ${money(c.suggestedScore)}/${money(c.maxScore)}，${escapeHtml(c.reason)}</li>`).join('')}</ul>` : ''}
        <div class="review-actions"><span>建议分：<strong>${money(result.suggestedScore)}</strong> / ${money(result.maxScore)}</span>
            <button class="button secondary small accept-score" ${result.gradingStatus !== 'SUCCESS' ? 'disabled' : ''}>接受建议分</button>
            <label class="field">实际分<input class="actual-score" type="number" min="0" max="${result.maxScore}" step="0.01" value="${result.actualScore ?? result.suggestedScore ?? ''}"></label>
            <button class="button success small set-score" ${result.gradingStatus === 'RUNNING' ? 'disabled' : ''}>确认实际分</button>
            <button class="button ghost small retry-result" ${result.gradingStatus !== 'FAILED' || result.reviewStatus === 'CONFIRMED' ? 'disabled' : ''}>重试自动评分</button></div></article>`;
}

function bindReviewActions(card, result, submissionId) {
    card.querySelector('.accept-score').onclick = () => submitReview(result, 'ACCEPT_SUGGESTION', null, submissionId);
    card.querySelector('.set-score').onclick = () => {
        const input = card.querySelector('.actual-score').value.trim();
        if (input === '' || !Number.isFinite(Number(input))) {
            notify('请输入有效的实际分数。', 'error');
            return;
        }
        submitReview(result, 'SET_SCORE', Number(input), submissionId);
    };
    card.querySelector('.retry-result').onclick = async () => { try { await api(`/api/grading/results/${result.id}/retry`, {method:'POST'}); await openReview(submissionId); } catch(error){ notify(error.message,'error'); } };
}

async function submitReview(result, action, actualScore, submissionId) {
    try {
        await api(`/api/grading/results/${result.id}/review`, {method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify({action, actualScore, expectedVersion:result.version})});
        await Promise.all([openReview(submissionId), loadClassResults()]); notify('实际分已确认。', 'success');
    } catch (error) { notify(error.message, 'error'); }
}

function showTab(name) {
    document.querySelectorAll('.tab').forEach(tab => tab.classList.toggle('active', tab.dataset.tab === name));
    document.querySelectorAll('.tab-panel').forEach(panel => panel.classList.toggle('hidden', panel.id !== `tab-${name}`));
    if (name === 'import') loadLatestBatch();
    if (name === 'grading') loadLatestTask();
    if (name === 'results') loadClassResults();
}

$('refresh-exams').onclick = () => loadExams(state.exam?.id);
$('show-create-exam').onclick = showCreateExam;
$('cancel-create-exam').onclick = () => { $('create-exam-panel').classList.add('hidden'); state.exam ? $('exam-workspace').classList.remove('hidden') : $('empty-state').classList.remove('hidden'); };
$('add-question').onclick = () => addQuestion();
$('create-exam').onclick = createExam;
$('confirm-standards').onclick = confirmStandards;
$('upload-zip').onclick = uploadZip;
$('import-confirmed').onclick = importConfirmed;
$('start-grading').onclick = startGrading;
$('retry-grading').onclick = retryGrading;
$('refresh-results').onclick = loadClassResults;
$('close-review').onclick = () => $('review-drawer').classList.add('hidden');
document.querySelectorAll('.tab').forEach(tab => tab.onclick = () => showTab(tab.dataset.tab));

loadExams();
