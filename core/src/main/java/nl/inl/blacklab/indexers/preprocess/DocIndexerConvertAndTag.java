package nl.inl.blacklab.indexers.preprocess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.ReaderInputStream;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.DocumentFormats;

public class DocIndexerConvertAndTag extends DocIndexer {

	PushbackInputStream converterInput;
	/** Charset of the data for our converter and tagger input/output, might be null if our converter/tagger do not use charsets (because they process binary data for example). */
	Charset charset;

	private ConvertPlugin converter;
	private TagPlugin tagger;

	public DocIndexerConvertAndTag(ConvertPlugin converter, TagPlugin tagger) {
		this.converter = converter;
		this.tagger = tagger;
	}

	@Override
	public void close() throws Exception {
		//
	}

	/**
	 * Use {@link DocIndexerConvertAndTag#setDocument(InputStream, Charset) if at all possible. }
	 */
	@Override
	public void setDocument(Reader reader) {
		// Reader outputs chars, so we can determine our own charset when we put them back into a stream
		// We just need to make sure to pass it on to whatever consumes the stream
		converterInput = new PushbackInputStream(new ReaderInputStream(reader, StandardCharsets.UTF_8), 251);
		charset = StandardCharsets.UTF_8;
	}

	@Override
	public void setDocument(InputStream is, Charset cs) {
		converterInput = new PushbackInputStream(is, 251);
		charset = cs;
	}

	@Override
	public void index() throws Exception {
		if (this.converterInput == null)
			throw new IllegalStateException("A document must be set before calling index()");

		Reader indexerInput = null;

		/*
		 * If the converter can't handle the file,
		 * we assume that the file is already in the output format, and we attempt to index it directly.
		 * This isn't entirely correct when the file is in a format neither the converter nor the indexer can handle, but that is technically user error.
		 */
		if (converter.canConvert(converterInput, charset, FilenameUtils.getExtension(this.documentName).toLowerCase())) {
			// ByteArrayOutputStream close() can conveniently be read and reused even after close()
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			final Charset intermediateCharset = StandardCharsets.UTF_8;
			converter.perform(converterInput, charset, FilenameUtils.getExtension(this.documentName).toLowerCase(), output);
			
			Reader taggerInput = new InputStreamReader(new ByteArrayInputStream(output.toByteArray()), intermediateCharset);
			output.reset();
			try (OutputStreamWriter w = new OutputStreamWriter(output, intermediateCharset)) {
				tagger.perform(taggerInput, w);				
			}
			indexerInput = new InputStreamReader(new ByteArrayInputStream(output.toByteArray()), intermediateCharset);
		} else {
			indexerInput = new InputStreamReader(converterInput, charset);
		}

		DocIndexer outputIndexer = DocumentFormats.get(tagger.getOutputFormatIdentifier(), this.indexer, tagger.getOutputFileName(this.documentName), indexerInput);
		outputIndexer.index();
	}

	@Override
	protected int getCharacterPosition() {
		return 0;
	}

	@Override
	public void reportCharsProcessed() {
		//
	}

	@Override
	public void reportTokensProcessed() {
		//
	}
}
