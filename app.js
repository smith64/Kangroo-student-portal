function $(id){ return document.getElementById(id); }

function showPrograms(){
  try{
    const json = window.javaBridge.getProgramsJson();
    const progs = JSON.parse(json);
    const list = $('progList');
    const sel = $('programSelect');
    list.innerHTML = '';
    sel.innerHTML = '';
    progs.forEach(p => {
      const li = document.createElement('li');
      li.textContent = `${p.code} - ${p.name} : ${p.description}`;
      list.appendChild(li);
      const opt = document.createElement('option');
      opt.value = p.id;
      opt.textContent = `${p.code} - ${p.name}`;
      sel.appendChild(opt);
    });
  }catch(e){
    console.error(e);
  }
}

$('helpMenu').addEventListener('change', (e)=>{
  const v = e.target.value;
  if(v==='about') alert('Kangaroo University Application Portal\nVersion 1.0');
  if(v==='contact') alert('Email: admissions@kangaroo.ac\nPhone: +123456789');
  e.target.value='';
});

$('payBtn').addEventListener('click', ()=>{
  const amount = parseFloat($('fee').value)||0;
  try{
    const res = window.javaBridge.makePayment(amount);
    const obj = JSON.parse(res);
    $('out').textContent = `Payment done. Ref: ${obj.reference}`;
    window.lastPayment = obj;
  }catch(e){ console.error(e); alert('Payment failed'); }
});

// Use native chooser for files
$('gradeFile').addEventListener('click', async (e)=>{
  try{
    const p = window.javaBridge.chooseFile();
    if(p) {
      $('gradeFile').dataset.path = p;
      $('gradeName').textContent = p;
    }
  }catch(e){ console.error(e); }
});
$('nrcFile').addEventListener('click', async (e)=>{
  try{
    const p = window.javaBridge.chooseFile();
    if(p) {
      $('nrcFile').dataset.path = p;
      $('nrcName').textContent = p;
    }
  }catch(e){ console.error(e); }
});

$('submitBtn').addEventListener('click', ()=>{
  const name = $('fullName').value.trim();
  const nrc = $('nrc').value.trim();
  const programId = parseInt($('programSelect').value);
  if(!name||!nrc||isNaN(programId)) { alert('Provide name, nrc and program'); return; }
  const gradePath = $('gradeFile').dataset.path || '';
  const nrcPath = $('nrcFile').dataset.path || '';
  try{
    const res = window.javaBridge.submitApplication(name, nrc, programId, gradePath, nrcPath);
    const obj = JSON.parse(res);
    alert('Submitted: ID ' + obj.id);
  }catch(e){ console.error(e); alert('Submit failed'); }
});

setTimeout(showPrograms, 500);
