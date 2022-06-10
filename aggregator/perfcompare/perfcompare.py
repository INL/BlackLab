# Run comparison performance tests between two BlackLab servers.
# Prints tab-separated timing per server.
# See README.md.

import urllib.request
import time
import json
import statistics
from urllib.parse import urlencode, quote_plus

def timeToGroup(corpus_url, word, groupby, repeat):
    """ Returns average time for a grouping query on a server.

    Will repeat a grouping query a number of times (after a single warmup query)
    and returns the average response time.
    """
    payload = {
        'patt': f'[word="{word}"]',
        'group': groupby,
        'usecache': 'no'
    }
    result = urlencode(payload, quote_via=quote_plus)
    url = f'{corpus_url}/hits?{result}'
    duration = []
    for i in range(repeat + 1):  # 1 extra for warmup
        start = time.time()
        with urllib.request.urlopen(url) as response:
           html = response.read()
        d = round(time.time() - start, 2)
        if i > 0: # first is warmup
            duration.append(d)

    # return average duration
    return round(statistics.mean(duration), 2)

def timeToGroupCompare(corpora, word, groupby, repeat):
    """ Prints tab-separated average response time for a grouping query on different servers """
    duration = []
    for url in corpora:
        duration.append(timeToGroup(url, word, groupby, repeat))
    str_dur = "\t".join(map(str, duration))
    print(f'{word}\t{str_dur}')

def timeToGroupCompareWords(corpora, run):
    """ Performs comparisons of grouping queries and prints tab-separated results """
    print(f'# group by {run["groupby"]}')
    for word in run['words']:
        timeToGroupCompare(corpora, word, run['groupby'], run['repeat'])

def main():
    # Read config file
    with open('perfcompare.json') as f:
       config = json.load(f)

    # Perform the configured runs
    for run in config['runs']:
        run['repeat'] = run['repeat'] if 'repeat' in run else config['repeat']
        timeToGroupCompareWords(config['corpora'], run)

if __name__ == '__main__':
    main()
