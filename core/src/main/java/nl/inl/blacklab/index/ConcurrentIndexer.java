package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.index.CorruptIndexException;

import nl.inl.util.ExUtil;
import nl.inl.util.FileProcessor;
import nl.inl.util.FileProcessor.ErrorHandler;

public class ConcurrentIndexer extends Indexer {
	
	private static class ExceptionCatchingThreadFactory implements ThreadFactory {
	    private final ThreadFactory delegate;
	
	    private ExceptionCatchingThreadFactory(ThreadFactory delegate) {
	        this.delegate = delegate;
	    }
	
	    public Thread newThread(final Runnable r) {
	        Thread t = delegate.newThread(r);
	        t.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
	            @Override
	            public void uncaughtException(Thread t, Throwable e) {
	                System.err.println("Uncaught exception during indexing:");
	            	e.printStackTrace();
	            }
	        });
	        return t;
	    }
	}

	private final class IndexFileTask implements Runnable {
		private final File fileToIndex;
		private final boolean recurseSubdirs;
		private final String glob;
	
		private IndexFileTask(File fileToIndex, boolean recurseSubdirs, String glob) {
			this.fileToIndex = fileToIndex;
			this.recurseSubdirs = recurseSubdirs;
			this.glob = glob;
		}
	
		@Override
		public void run() {
			try {
			    FileProcessor proc = new FileProcessor(recurseSubdirs, recurseSubdirs && processArchivesAsDirectories);
			    proc.setFileNameGlob(glob);
		        proc.setFileHandler(new FileProcessor.FileHandler() {
		            @Override
		            public void stream(String path, InputStream is) {
		                try {
		                    index(path, is);
		                } catch (Exception e) {
		                    throw ExUtil.wrapRuntimeException(e);
		                }
		            }

		            @Override
		            public void file(String path, File f) {
		                try {
		                    // Regular file.
		                    DocIndexer docIndexer = getDocIndexerFactory().get(ConcurrentIndexer.this, path, f, DEFAULT_INPUT_ENCODING);
		                    indexDocIndexer(path, docIndexer);
		                } catch (Exception e) {
		                    throw ExUtil.wrapRuntimeException(e);
		                }
		            }
		        });
			    proc.setErrorHandler(new ErrorHandler() {
		            @Override
		            public boolean errorOccurred(String file, String msg, Exception e) {
		                log("*** Error indexing " + file, e);
		                return getListener().errorOccurred(e.getMessage(), "file", new File(file), null);
		            }
		        });
			    proc.processFile(fileToIndex);
			} catch (Exception e) {
				log("*** Error indexing " + fileToIndex, e);
				terminateIndexing = !getListener().errorOccurred(e.getMessage(), "file", fileToIndex, null);
			}
		}
	}

	private ThreadPoolExecutor executor;
	
	public ConcurrentIndexer(File directory, boolean create, DocIndexerFactory docIndexerFactory,
			File indexTemplateFile) throws DocumentFormatException, IOException {
		super(directory, create, docIndexerFactory, indexTemplateFile);
		initThreadPool();
	}

	@Deprecated
	public ConcurrentIndexer(File directory, boolean create, Class<? extends DocIndexer> docIndexerClass,
			File indexTemplateFile) throws DocumentFormatException, IOException {
		super(directory, create, docIndexerClass, indexTemplateFile);
		initThreadPool();
	}

	@Deprecated
	public ConcurrentIndexer(File directory, boolean create, Class<? extends DocIndexer> docIndexerClass)
			throws IOException, DocumentFormatException {
		super(directory, create, docIndexerClass);
		initThreadPool();
	}

	@Deprecated
	public ConcurrentIndexer(File directory, boolean create) throws IOException, DocumentFormatException {
		super(directory, create);
		initThreadPool();
	}

	private void initThreadPool() {
		executor = (ThreadPoolExecutor)Executors.newFixedThreadPool(8);
		executor.setThreadFactory(new ExceptionCatchingThreadFactory(executor.getThreadFactory()));
	}

	@Override
	protected void indexInternal(final File fileToIndex, final String glob, final boolean recurseSubdirs)  {
		Runnable runnable = new IndexFileTask(fileToIndex, recurseSubdirs, glob);
		executor.execute(runnable);
	}

	@Override
	public void close() throws CorruptIndexException, IOException {
		executor.shutdown();
		try {
			// Wait for all threads to finish (Long.MAX_VALUE == "forever")
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			System.err.println("Error while waiting for threads to finish");
			e.printStackTrace();
		}
		super.close();
	}
}
