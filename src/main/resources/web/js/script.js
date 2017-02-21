
/* Please keep this file [✓] UTF-8 encoded */


function getCoords(elem) { // crossbrowser version
    var box = elem.getBoundingClientRect();

    var body = document.body;
    var docEl = document.documentElement;

    var scrollTop = window.pageYOffset || docEl.scrollTop || body.scrollTop;
    var scrollLeft = window.pageXOffset || docEl.scrollLeft || body.scrollLeft;

    var clientTop = docEl.clientTop || body.clientTop || 0;
    var clientLeft = docEl.clientLeft || body.clientLeft || 0;

    var top  = box.top +  scrollTop - clientTop;
    var left = box.left + scrollLeft - clientLeft;

    return { top: Math.round(top), left: Math.round(left) };
}


jQuery(function() {
  // jQuery is .ready() - let's do stuff!
  
  
  // remove noscript class to ignore noscript fallback css
  jQuery("body").removeClass("noscript");
    
  // hide scrollToTop-Button when we're already there
  jQuery( window ).scroll(function(){
    if (jQuery( document ).scrollTop() < 100) {
      jQuery(".topButton:not(:animated)").stop().fadeOut();
    } else {
      jQuery(".topButton:not(:animated)").stop().fadeIn();
    }
  });
  jQuery( window ).scroll();
  
  
  // smooth scolling for all anchor links
  jQuery('a[href^="#"]:not(.anchor)').on('click',function (e) {
    var target = this.hash;
    var tID = document.getElementById(target.split('#').join(''));
    if (tID != null) {
      e.preventDefault();
      var offset = getCoords(tID);
      jQuery('html,body').animate({scrollTop: offset.top},900, function(){
        window.location.hash = target;
      });
    }
  });
  
  
  // make main menu items with dropdowns clickable again:
  jQuery('.dropdown-toggle').click(function(){
      location.href = jQuery(this).attr('href');
  });
  

});


