var parser = "CorpusQL";

var sessionId = guid(); // our session id

var waitingForResponse = false;

// Initialize the application
$(document).ready(function () {
    print("Welcome to the BlackLab Query Tool\n\n");
    print("Type commands in the input field at the bottom and press Enter to execute.\n\n");
    executeCommand("help");
	$('#textfield').focus();
    resizeElements();
});

// Keep elements sized to the window size
$(window).resize(function() {
    resizeElements();
});

// Always re-set focus on the text field when user types
$(document).keypress(function(event) {
    $('#textfield').focus();
});

// Generate a GUID
function guid() {
    // (from http://stackoverflow.com/questions/105034/how-to-create-a-guid-uuid-in-javascript/2117523#2117523)
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random()*16|0, v = c == 'x' ? r : (r&0x3|0x8);
        return v.toString(16);
    });
}

// Size elements to the window size
function resizeElements() {
    $("#output").height($(window).height() - 90);
    $("#textfield").width($(window).width() - 170);
}

// Output to the console
function print(str) {
    var output = $('#output');
    output.append(escapeHtml(str));
	output.scrollTop(output[0].scrollHeight);
}

// Output to the console
function printCommand(prompt, command) {
    var output = $('#output');
    var link = $('<a>',{
        text: command,
        title: 'Repeat/edit this command',
        href: '#',
        click: function () {
            $('#textfield').val(command);
            $('#textfield').focus();
            return false;
        }
    });
    output.append("<b>" + prompt).append(link).append("</b>\n");
    output.scrollTop(output[0].scrollHeight);
}

// Used to escape HTML special characters
var entityMap = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': '&quot;',
    "'": '&#39;',
    "/": '&#x2F;'
};

// Escape HTML special characters
function escapeHtml(string) {
    return String(string).replace(/[&<>"'\/]/g, function (s) {
        return entityMap[s];
    });
}

// Execute a command on the server and show the output
function executeCommand(command) {
    setWaitingForResponse(true);
    $.ajax({
        type: "POST",
        url: "QueryTool",
        data: { command: command, sessionId: sessionId }
    }).done(function( msg ) {
    
        // Different parser..?
        var switchMsg = msg.substr(0, 13);
        if (switchMsg == "Switching to ") {
            var switchTo = msg.substr(13, 4);
            if (switchTo == "Corp")
                parser = "CorpusQL";
            else if (switchTo == "Cont")
                parser = "ContextQL";
            else if (switchTo == "Luce")
                parser = "Lucene";
            else
                parser = switchTo;
            $('#parserName').text(parser);
        }
        
        // Print output
        print(msg + "\n");
        setWaitingForResponse(false);
    });
}

function setWaitingForResponse(b) {
    waitingForResponse = b;
    $('#textfield').css("background-color", b ? "#444" : "black");
}

function isWaitingForResponse() {
    return waitingForResponse;
}

// The user entered a command. Execute it.
function enteredCommand() {
    if (isWaitingForResponse())
        return; // Don't execute next command if previous command not done yet
        
    // Get command and clear input
    var command = $('#textfield').val();
    $('#textfield').val('');
    
    // Print command, execute and print results
	printCommand(parser + "> ", command);
	executeCommand(command);
}

