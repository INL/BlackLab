export default ({ router }) => {
  router.addRoutes([
    { path: '/learn.html', redirect: '/guide' },
    { path: '/getting-started.html', redirect: '/guide/getting-started.html' },
    { path: '/corpus-query-language.html', redirect: '/guide/corpus-query-language.html' },
    { path: '/blacklab-server-overview.html', redirect: '/server/' },
    { path: '/configuration-files.html', redirect: '/server/configuration.html' },
    { path: '/blacklab-server-different-languages.html', redirect: '/server/from-different-languages.html' },
    { path: '/indexing-with-blacklab.html', redirect: '/guide/indexing-with-blacklab.html' },
    { path: '/add-input-format.html', redirect: '/guide/how-to-configure-indexing.html' },
    { path: '/improve-search-speed.html', redirect: '/server/vmtouch.html' },
    { path: '/file-formats.html', redirect: '/development/#internals' },
    { path: '/downloads.html', redirect: '/development/downloads.html' },
    { path: '/faq.html', redirect: '/guide/faq.html' },
    { path: '/changelog.html', redirect: '/development/changelog.html' },
    { path: '/roadmap.html', redirect: '/guide/future-plans.html' },
    { path: '/migration-guide.html', redirect: '/development/migration-guide.html' }
    // TODO: add the rest
  ]);
};
