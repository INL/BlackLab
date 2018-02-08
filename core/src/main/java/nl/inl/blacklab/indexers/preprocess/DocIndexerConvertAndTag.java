package nl.inl.blacklab.indexers.preprocess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FilenameUtils;

import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.DocumentFormats;

public class DocIndexerConvertAndTag extends DocIndexer {

	Reader converterInput;

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

	@Override
	public void setDocument(Reader reader) {
		converterInput = reader;
	}

	// TODO manage streams
	@Override
	public void index() throws Exception {
		if (this.converterInput == null)
			throw new IllegalStateException("A document must be set before calling index()");

		final Charset intermediateCharset = StandardCharsets.UTF_8;

		// Use ByteArrayOutputStream on purpose, as we can reuse it and it allows us to read the data without hitting the filesystem
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		converter.perform(converterInput, FilenameUtils.getExtension(this.documentName).toLowerCase(), new OutputStreamWriter(output, intermediateCharset));

		Reader taggerInput = new InputStreamReader(new ByteArrayInputStream(output.toByteArray()), intermediateCharset);
		System.out.println("test");
		output.reset(); // reuse output memory, safe since the data was already copied by .toByteArray()
		tagger.perform(taggerInput, new OutputStreamWriter(output, intermediateCharset));

		Reader indexerInput = new InputStreamReader(new ByteArrayInputStream(output.toByteArray()), intermediateCharset);
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
