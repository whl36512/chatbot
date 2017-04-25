if (window.console) {
	console.log("Welcome");
}

//(function() {
//	var dialog = document.getElementById('window');
//	document.getElementById('show').onclick = function() {
//		dialog.show();
//	};
//	document.getElementById('exit').onclick = function() {
//		dialog.close();
//	};
//})();

function toggleDisply(id) {
	//id comes in as either 12345 or toggleBy-12345. If it already has toggleBy- on it, do not prepend toggleBy- again
	console.log("toggleDisplay id=" + id)
	if ( id.indexOf("toggleBy-") != 0 ) id="toggleBy-" + id
	var elem = document.getElementById(id);
	// alert("elem=" + elem)
	elem.style.display = (elem.style.display == "block") ? "none" : "block";
	false;
}

// var httpRequest;
function flag(id) {
	var url= "/flag?msgId="+ id ;
	var httpRequest = new XMLHttpRequest();

	if (!httpRequest) {
		alert('Giving up :( Cannot create an XMLHTTP instance');
		return false;
	}
	httpRequest.onreadystatechange = function () {alertContents(httpRequest);};
	httpRequest.open('GET', url);
	httpRequest.send();
}

function alertContents(httpRequest) {
	
	if (httpRequest.readyState === XMLHttpRequest.DONE) {
		if (httpRequest.status === 200) {
			alert(httpRequest.responseText);
			window.location.reload(true); 
			
		} 
		else {
			alert('ERROR 20170117005623: return status '+ httpRequest.status + "\n" + httpRequest.responseText.replace(/\s+/g,''));
		}
	}
}

function submitForm(oFormElement, validateFunc)
{
	// alert ("enter submitForm");
	var formData =getFormData(oFormElement);
	formData =validateFunc(formData)
	if (formData==null) return false ;
	var urlEncoded=urlEncodedData(formData);
	var xhr = new XMLHttpRequest();
	// console.log("encodedData="+urlEncoded)
	
	xhr.onreadystatechange = function(){ alertContents(xhr); } ;	
	xhr.open (oFormElement.method, oFormElement.action, true);
	xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
	xhr.setRequestHeader('Content-Length', urlEncoded.length);
	xhr.send (urlEncoded);
	// alert ("after send");
	return false;
}

function validURL(str) {
	  var pattern = new RegExp('^(https?:\/\/)?'+ // protocol
	    '((([a-z\d]([a-z\d-]*[a-z\d])*)\.)+[a-z]{2,}|'+ // domain name
	    '((\d{1,3}\.){3}\d{1,3}))'+ // OR ip (v4) address
	    '(\:\d+)?(\/[-a-z\d%_.~+]*)*'+ // port and path
	    '(\?[;&a-z\d%_.~+=-]*)?'+ // query string
	    '(\#[-a-z\d_]*)?$','i'); // fragment locater
	  if(!pattern.test(str)) {
		alert ("ERROR 201612292224: Invalide URL");
	    return false;
	  } else {
	    return true;
	  }
	}

function validateCommentForm(formData)
{
	var content=formData.get("content");
	if ( content.length<30 || content.length>4000)
	{
		alert ("ERROR 201612292153: Comment length must be between 30 and 4000");
		return null;
	}
	return formData;
}

function validateTopicForm(formData)
{
	return formData;
}


function getFormData(form)
{
	var formData = new Map();
	var elemArray = [];
	function pushElement(tagName)
	{ 
		var elems = form.getElementsByTagName(tagName) ;
		console.log("length="+elems.length)
        for (var i = 0; i < elems.length; i++)
		{
        	console.log("i="+i+" elem="+elems[i])
			elemArray.push (elems[i]);
		}
	}
	pushElement("input") ;
	// alert (elemArray);
	pushElement("textarea") ;
	// alert ("length=" +elemArray.length);
	for (var i = 0; i < elemArray.length; i++)
	{
		var name =elemArray[i].getAttribute("name");
		var value =elemArray[i].value ;
		formData.set (name, value) ;
	}
	console.log(formData)
	return formData;

}


function urlEncodedData(formData){
	console.log("in urlEncodedData");
	console.log(formData);
	
	var urlEncodedData = "";
	var urlEncodedDataPairs = [];
//	for (var [k, v] of formData){
//		// alert("name="+name +" value=" + value)
//	    urlEncodedDataPairs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
//		
//	}
	formData.forEach(function (v,k){
		console.log("k="+k);
		urlEncodedDataPairs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
	
	})
	urlEncodedData = urlEncodedDataPairs.join('&').replace(/%20/g, '+');
	return urlEncodedData;
}
