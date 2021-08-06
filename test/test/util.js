function toBeDocInfo(expectDocInfo) {
    expectDocInfo.to.be.an("object").that.has.all.keys(
        "fromInputFile", "lengthInTokens", "mayView", "pid", "title"
    );
}

function toBeContextPart(expectPart) {
    expectPart.to.be.an("object").that.has.all.keys(
        'punct',
        'lemma',
        'pos',
        'word'
    );
}

module.exports = {
    toBeDocInfo,
    toBeContextPart,
};
