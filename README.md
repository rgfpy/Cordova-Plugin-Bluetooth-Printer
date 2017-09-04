# Cordova-Plugin-Bluetooth-Printer
A cordova plugin for bluetooth printer for android platform, which support text printing and POS printing.

##Support
- Text
- POS Commands
- Image Printing (todo)
- Barcode Printing (todo)

##Install
Using the Cordova CLI and NPM, run:

```
cordova plugin add https://github.com/rgfpy/Cordova-Plugin-Bluetooth-Printer.git --save
```



##Usage
Get list of paired bluetooth printers

```
BTPrinter.list(function(data){
        console.log("Success");
        console.log(data); //list of printer in data array
    },function(err){
        console.log("Error");
        console.log(err);
    });
```


* First invoke this option to Connect printer

```
BTPrinter.connect(function(data){
	console.log("Success");
	console.log(data);
},function(err){
	console.log("Error");
	console.log(err);
}, "PrinterName");
```


Disconnect printer

```
BTPrinter.disconnect(function(data){
	console.log("Success");
	console.log(data);
},function(err){
	console.log("Error");
	console.log(err);
}, "PrinterName");
```


Disconnect printer

```
BTPrinter.disconnect(function(data){
	console.log("Success");
	console.log(data);
},function(err){
	console.log("Error");
	console.log(err);
});
```


Print simple string (After invoke BTPrinter.connect())

```
BTPrinter.printText(function(data){
    console.log("Success");
    console.log(data);
},function(err){
    console.log("Error");
    console.log(err);
}, "String to Print");
```


Print image

```
BTPrinter.printText(function(data){
    console.log("Success");
    console.log(data);
},function(err){
    console.log("Error");
    console.log(err);
}, "Image Base64 String");
```

```
BTPrinter.print(function(data){
    console.log("Success");
    console.log(data);
},function(err){
    console.log("Error");
    console.log(err);
}, "Base64 String of Image");
```


POS printing

```
BTPrinter.printPOSCommand(function(data){
    console.log("Success");
    console.log(data);
},function(err){
    console.log("Error");
    console.log(err);
}, "0C");
//OC is a POS command for page feed
```
