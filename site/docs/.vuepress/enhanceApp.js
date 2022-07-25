export default ({ router }) => {
  router.addRoutes([
    { path: '/add-input-format.html', redirect: '/guide/how-to-configure-indexing.html' },
    { path: '/blacklab-in-action.html', redirect: '/' },
    { path: '/blacklab-internals.html', redirect: '/development/#internals' },
    { path: '/blacklab-server-different-languages.html', redirect: '/server/from-different-languages.html' },
    { path: '/blacklab-server-overview.html', redirect: '/server/' },
    { path: '/blog.html', redirect: '/' },
    { path: '/changelog.html', redirect: '/development/changelog.html' },
    { path: '/configuration-files.html', redirect: '/server/configuration.html' },
    { path: '/corpus-query-language.html', redirect: '/guide/corpus-query-language.html' },
    { path: '/corpus-search-engine.html', redirect: '/' },
    { path: '/downloads.html', redirect: '/development/downloads.html' },
    { path: '/example-application.html', redirect: '/' },
    { path: '/faq.html', redirect: '/guide/faq.html' },
    { path: '/features.html', redirect: '/' },
    { path: '/file-formats.html', redirect: '/development/#internals' },
    { path: '/getting-started.html', redirect: '/guide/getting-started.html' },
    { path: '/how-to-configure-indexing.html', redirect: '/' },
    { path: '/improve-search-speed.html', redirect: '/server/vmtouch.html' },
    { path: '/indexing-with-blacklab.html', redirect: '/guide/indexing-with-blacklab.html' },
    { path: '/install-macos.html', redirect: '/' },
    { path: '/learn.html', redirect: '/guide' },
    { path: '/migration-guide.html', redirect: '/development/migration-guide.html' }
    { path: '/newsletter.html', redirect: '/' },
    { path: '/plugins.html', redirect: '/' },
    { path: '/query-tool.html', redirect: '/' },
    { path: '/roadmap.html', redirect: '/guide/future-plans.html' },
    { path: '/tsv-example.html', redirect: '/guide/tsv-example.html' }
    { path: '/who-uses-blacklab.html', redirect: '/' },
    { path: '/xpath_examples.html', redirect: '/' }
  ]);
};

// TODO: fill in the blanks