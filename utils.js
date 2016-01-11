    function contains(tar, srch) {
        return tar.indexOf(srch) != -1;
    }
    function arrItem(arr, key) {
        var index = arr.indexOf(key);
        return index==-1 ? null : arr[index];
    }
    function thisOrThat(str, one, two) {
        return contains(str, one) ? one : two;
    }
    function popCatVals(catName, v, categoryConfig) {
        var categoryValues = arrItem(categoryConfig, catName);
        if(categoryValues!=null && arrItem(categoryValues[catName], v)==null) {
            categoryValues[catName].push(v);                              
        }
        return v;
    }
    function pushx(arr, value) {
        if(arrItem(arr, value)==null) {
            arr.push(value);
            return true;
        }
        return false;
    }
