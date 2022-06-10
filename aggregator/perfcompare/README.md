# perfcompare

A simple tool to compare grouping performance of two BLS servers. Used to compare distributed search with non-distributed search.

Configuration is done with a file `perfcompare.json` (no comments allowed in actual file):

```jsonc
{

  // BLS servers to query. These should use the same corpus. 
  "corpora": [
    "http://server1.local/blacklab-server/my-corpus",
    "http://server2.local/blacklab-server/my-corpus"
  ],
  
  // Number of times to repeat each query after warmup.
  // (a single warmup is always done to exclude OS disk cache effects;
  //  the warmup doesn"t count for the average timing)
  "repeat": 2,
  
  // The comparison runs to perform
  "runs": [
    {
      // Words to query (actual query will be [word="..."])
      "words": ["quick", "brown", "fox", "jumps", "lazy", "dog"],
      
      // Property to group by
      "groupby": "wordleft:lemma:i"
    },
  ]
}
```

Output will look like this:

```tsv
# group by wordleft:lemma:i
quick	0.76	2.16
brown	0.92	1.88
fox	0.98	1.99
jumps	6.27	10.28
lazy	12.12	23.55
dog	28.9	65.32
```

Each run will start with a comment that indicates the grouping property. Then a tab-separated line is printed with the word, followed by average timings per corpus (in the order they were specified in the `"corpora"` key). 
