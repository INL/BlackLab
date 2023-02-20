// print process.argv
const { readFile, readdir } = require('fs/promises');
const http = require('http');

/**
 * 
 * @param {string} postData 
 * @param {Record<string, string>} query 
 * @returns 
 */
async function send(postData, query, url='/solr/blacklab/update') {
  function querystring() {
    return Object.entries(query || {}).reduce((q, [key, value]) => {
      const part = `${encodeURIComponent(key)}=${encodeURIComponent(value)}`;
      return q ? `${q}&${part}` : `?${part}`;
    }, '');
  }

  
  query = querystring();
  /** @type {http.RequestOptions} */
  var options = {
    hostname: 'localhost',
    port: 8983,
    path: url + query,
    method: 'POST',
    headers: {
      'Content-Type': postData ? 'application/xml' : 'text/plain',
      'Content-Length': postData ? Buffer.byteLength(postData, 'utf8') : 0
    },
  };

  return new Promise((resolve, reject) => {
    var req = http.request(options, (res) => {
      res.on('data', d => {
        if (!(res.statusCode >= 200 && res.statusCode < 300)) reject(d)
        else resolve(d);
      });
    });
    req.on('error', reject);
    if (postData) req.write(postData, 'utf8');
    req.end();
  });
}


(async () => {

   

  // attempt to create core
  try {
    const r = await send(null, {
      action: 'CREATE', 
      name: 'blacklab', 
      instanceDir: 'blacklab',
      configSet: 'blacklab',
      // schema: '../configsets/blacklab/managed-schema',
      // config: '../configsets/blacklab/solrconfig.xml',
      // dataDir: 'data',
    }, `/solr/admin/cores`)
    console.log('core created?'); 
    console.log(String(r));
  } catch (e) {
    e = String(e);
    if (!e.includes("exists")) {
      console.log(String(e))
      return;
    } else {
      console.log('Core already exists. Skipping creation.')
    }
  }
  // return;
    
    
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
