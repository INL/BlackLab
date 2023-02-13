// print process.argv
const { readFile, readdir } = require('fs/promises');
const http = require('http');

/**
 * 
 * @param {string} postData 
 * @param {Record<string, string>} query 
 * @returns 
 */
async function send(postData, query) {
  function querystring() {
    return Object.entries(query || {}).reduce((q, [key, value]) => {
      const part = `${encodeURIComponent(key)}=${encodeURIComponent(value)}`;
      return q ? `${q}&${part}` : `?${part}`;
    }, '');
  }

  
  query = querystring();
  console.log(query);

  /** @type {http.RequestOptions} */
  var options = {
    hostname: 'localhost',
    port: 8983,
    path: '/solr/blacklab/update' + query,
    method: 'POST',
    headers: {
      'Content-Type': 'application/xml',
      'Content-Length': Buffer.byteLength(postData, 'utf8')
    },
  };

  // Object.entries(query, ([k, v]) => options.headers[k] = encodeURIComponent(v));

  return new Promise((resolve, reject) => {
    var req = http.request(options, (res) => {
      res.on('data', d => {
        if (!(res.statusCode >= 200 && res.statusCode < 300)) reject(d)
        else resolve(d);
      });
    });
    req.on('error', reject);
    req.write(postData, 'utf8');
    req.end();
  
  });
}


(async () => {
  // attempt to create core
  try {
    const r = await send('', `admin/cores?action=CREATE&name=blacklab&configSet=blacklab`)
    console.log('core created?'); 
    console.log(String(r));
  } catch (e) {
    console.log(String(e))
    return;
  }
    
    
  let config = process.argv[2];
  let datadir = process.argv[3];
  
  if (!config || !datadir) {
    console.log('usage: <format> <datadir>')
    return;
  }
  const configFileContents = await readFile(config, 'utf8'); 

  try {
    await send(configFileContents, {
      'bl.format': 'add',
      'bl': true,
      'bl.filename': config
    });
  } catch (e) {
    console.error(String(e));
  }
    
  
  const files = (await readdir(datadir)).filter(f => f.endsWith('xml'));
  
  for (const f of files) {
    const file = await readFile(datadir + '/' + f, 'utf8');
    console.log('posting ' + f);
    try {
      const r = await send(file, { 'bl.format': config, 'bl.filename': f, 'bl': "true" });
      console.log(String(r));
    } catch(e) {
      console.error(String(e));
      break;
    }
  }    
})();
