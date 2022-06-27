# Run comparison performance tests between two BlackLab servers.
# Prints tab-separated timing per server.
# See README.md.

import sys
import urllib.request
import time
import json
import statistics
from urllib.parse import urlencode, quote_plus

def clearCache(url):
    i = url.rindex('/')
    clear_url = url[:i] + '/cache-clear'
    #print(clear_url)

    # Perform a HTTP request with the POST method using clear_url as the url
    try:
        req =  urllib.request.Request(clear_url, method='POST')
        urllib.request.urlopen(req)
    except:
        print(f'Failed to clear cache on {clear_url}')
        raise

def timeRequest(corpus_url, repeat, request):
    """ Returns average time for a grouping query on a server.

    Will repeat a grouping query a number of times (after a single warmup query)
    and returns the average response time.
    """
    result = urlencode(request, quote_via=quote_plus)
    url = f'{corpus_url}/hits?{result}'
    print(f'# {url}')
    duration = []

    for i in range(repeat + 1):  # 1 extra for warmup
        # Clear the cache on each node;
        # otherwise we get instant results but aren't measuring anything
        clearCache(corpus_url)

        print(f'#   req {i+1}')
        start = time.time()
        try:
            with urllib.request.urlopen(url) as response:
               html = response.read()
        except urllib.error.HTTPError as e:
            print(f'URL {url}\nHTTPError: {e.code}\nRESPONSE: {e.read().decode()}')
            raise e
        d = round(time.time() - start, 2)
        print(f'#   took {d}s')
        if i > 0: # first is warmup
            duration.append(d)

    # return average duration
    return round(statistics.mean(duration), 2)


def timeCompareRequests(corpora, repeat, req):
    """ Prints tab-separated average response time for a grouping query on different servers """
    duration = []
    for url in corpora:
        duration.append(timeRequest(url, repeat, req))
    str_dur = "\t".join(map(str, duration))
    print(f'{req["patt"]}\t{str_dur}')


def executeRun(corpora, repeat, run):
    """ Performs comparisons of grouping queries and prints tab-separated results """
    config = { key:run[key] for key in run if key not in ['words', 'patts'] }
    print(f'# {config}')
    if 'words' in run:
        for word in run['words']:
            request = { **config, 'patt': f'"{word}"' }
            timeCompareRequests(corpora, repeat, request)
    if 'patts' in run:
        for patt in run['patts']:
            request = { **config, 'patt': patt }
            timeCompareRequests(corpora, repeat, request)


def main():
    if len(sys.argv) < 1:
        print('Usage: perfcompare.py <config.json>')
        sys.exit(1)

    # Read config file
    with open(sys.argv[1]) as f:
       config = json.load(f)

    # Perform the configured runs
    for run in config['runs']:
        executeRun(config['corpora'], config['repeat'], run)

if __name__ == '__main__':
    main()
