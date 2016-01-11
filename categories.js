var getCustomParameters = function(benchmarkJson) {
    return {
        BufferType:     {label: 'BufferType', type: 'string', optid : '', values : [], id: -1},
        ParseSource:    {label: 'ParseSource', type: 'string', optid : '', values : [], id: -1},
        Operation:      {label: 'Operation', type: 'string', optid : '', values : [], id: -1},
        Size:           {label: 'Size', type: 'number', optid : '', values : [], id: -1},
        SizeUnit:           {label: 'Unit', type: 'string', optid : '', values : [], id: -1}
    };  
}

/**
    Extracts the test name from the fully qualified benchmark name
 */
var extractTestName = function(benchmarkName) {
	var spl = benchmarkName.split(".");
    return spl[spl.length-1];
}

var extractParameterValues = function(testName) {   // e.g. "DirectStringRead118Kb"  -->  ["Direct", "String", "Read", "118", "Kb"]
    var parts = testName.match(/[A-Z]?[a-z]+|[0-9]+/g);
    return {
        "BufferType" : parts[0], 
        "ParseSource" : parts[1], 
        "Operation" : parts[2], 
        "Size" : parseInt(parts[3]),
        "SizeUnit" : parts[4]        
    }
}