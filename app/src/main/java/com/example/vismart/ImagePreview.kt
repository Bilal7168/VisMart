package com.example.vismart

import android.annotation.SuppressLint
import android.app.ActionBar.LayoutParams
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.transition.Visibility
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.TimeUnit
import androidx.camera.core.impl.utils.Exif
import androidx.compose.ui.text.toLowerCase
import org.json.JSONArray
import org.json.JSONObject
import android.speech.tts.TextToSpeech
import android.text.method.ScrollingMovementMethod
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginTop
import androidx.core.view.setMargins
import com.google.android.material.button.MaterialButton
import java.util.*
import kotlin.collections.HashMap


class ImagePreview : AppCompatActivity(),TextToSpeech.OnInitListener {
    private var filepath:String?=null
    private var current_img_bitmap:Bitmap ?= null

    private var prod_name:String?=null

    private var mypaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var tmpbmp:Canvas?=null;

    private var rotation:Int?=null;

    private var tts:TextToSpeech?=null;

    private var btnspeak:Button?=null;

    private var rect_arr:MutableList<RectF>?=null;

    private var object_texts:HashMap<RectF, String> = HashMap<RectF, String>(); //the string will keep appending to new characters found within the bounding box

    private var text_box_texts:HashMap<RectF, String> = HashMap<RectF, String>();
    //__________

    ////BUTTONS HASHMAP DYNAMIC WITH TEXT STRING
    private var dynamic_buttons:HashMap<MaterialButton, String> = HashMap<MaterialButton, String>();
    ///LAYOUT VIEW FINDER
    private var layout:RelativeLayout?=null;

    private var text_view:TextView?=null;

    private var detect_text:TextView?=null;
    @SuppressLint("RestrictedApi", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_preview)

        prod_name = (intent.getStringExtra("prod_name").toString()) //get the product name here and now need to find it
        Log.d("The text we got is: ", prod_name!!) //modified for search now

        var name = intent.getStringExtra("image_name")

        filepath = intent.getStringExtra("bitmap_location") //the file name of the bitmal

        rect_arr = intent.getParcelableArrayListExtra("detect_rects")

        layout = findViewById(R.id.layout)


        var btmp = BitmapFactory.decodeFile(filepath);

        var imgview = findViewById<ImageView>(R.id.imageView)

        imgview.requestLayout()

        text_view = findViewById<TextView>(R.id.details)
        text_view!!.movementMethod = ScrollingMovementMethod();
        text_view!!.isVerticalScrollBarEnabled = true;
        text_view!!.visibility = View.INVISIBLE;

        detect_text = findViewById(R.id.header_detect)
        detect_text!!.visibility = View.INVISIBLE



//        //need to rotate the btmp horizontally 90^
//
//        var matrix = Matrix()
//        matrix.postRotate("90".toFloat())
////
//        var imgview = findViewById<ImageView>(R.id.imageView)
////
//        var rotbtmp = Bitmap.createBitmap(btmp, 0, 0, btmp.width, btmp.height, matrix, true)
////
//        imgview.visibility = View.VISIBLE;
//        imgview.setImageBitmap(rotbtmp);
////
//        imgview.adjustViewBounds = true;

        //send this file to the rest api now
        //CHECKING FOR NO ROTATION
        var mut_btmp = btmp.copy(Bitmap.Config.ARGB_8888, true)
        current_img_bitmap = mut_btmp; //converts the rotated bmp to 90

        imgview.setImageBitmap(mut_btmp)
        imgview.visibility = View.VISIBLE

        recognizeImage();

    }



    private fun recognizeImage(){
        var _file = File(filepath)

        //var reqfile = MultipartBody.Part.createFormData("file", _file.name,
          //  _file.asRequestBody("image/*".toMediaTypeOrNull())
        //)

        //create Retrofit
        //REST API CLIENT SEND RESULTS

        var client = OkHttpClient().newBuilder()
            .connectTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .build()


        val mediaType = "text/plain".toMediaTypeOrNull()
        val body: RequestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("language", "eng")
            .addFormDataPart("isOverlayRequired", "true")
            .addFormDataPart("file", _file.name,
                _file.asRequestBody("image/*".toMediaTypeOrNull()))
            .addFormDataPart("iscreatesearchablepdf", "false")
            .addFormDataPart("issearchablepdfhidetextlayer", "false")
            .addFormDataPart("OCREngine", "5")
            .build()
        val request: Request = Request.Builder()
            .url("https://api.ocr.space/parse/image")
            .method("POST", body)
            .addHeader("apikey", "K87036609188957")
            .build()


        CoroutineScope(Dispatchers.IO).launch { //the coroutine scope (new thread for executing_
            // Do the POST request and get response
            var response = client.newCall(request).execute();

            withContext(Dispatchers.Main) {
                Log.d("within the context for dispatfch:", "working")
                if(response.isSuccessful) {
                    //REST API RESULTS MUST HAVE BEEN RETRIEVED RIGHT HERE
                    //GET THE JSON RESPONSE HERE AND START PARSING THE RESULTS AS WELL AS THE BOUNDING BOXES
                    response.body?.let { push_results_to_screen(it) } //using a safety function
                }
                else{
                    Log.d("response is unsuccessful", response.body.toString())
                }
            }
        }

    }

    private fun push_results_to_screen(_response: ResponseBody){
        val res = JSONObject(_response.string());
        Log.d("res : ", res.toString())
        try {
            val parse_res = res.getJSONArray("ParsedResults")
            val parse_res_obbj = parse_res.getJSONObject(0);
            val text_overlay = parse_res_obbj.getJSONObject("TextOverlay")
            val lines = text_overlay.getJSONArray("Lines")
            for (i in 0 until lines.length()) {
                val obj = lines.getJSONObject(i); //get the word objects
                val words = obj.getJSONArray("Words")
                //get all objects in this
                for (j in 0 until words.length()) {
                    val word = words.getJSONObject(j);
                    //check word match here
                    //if word exists in our product name we need to highlight it
                    val word_text = word_for_search_enhancer(word.getString("WordText").toString())
                    //comparison function to return an INT 1 for YES and INT 0 for NO
                    //return the left top height and width to a function
                    Log.d("Words found are: ", word.getString("WordText"))
                    Log.d("PRoduct name si: ", prod_name.toString())
                    val left = word.getInt("Left").toFloat()
                    val top = word.getInt("Top").toFloat()
                    val height = word.getInt("Height").toFloat()
                    val width = word.getInt("Width").toFloat()
                    //call spawner function
                    SpawnButtons(left, top, height, width, compare(word_text, word_for_search_enhancer(prod_name.toString())))

                    //text_box_integration
                    Log.d("Product nme: ", word_text)
                    var str_text_box = " "
                    if(text_box_texts.containsKey(RectF(left, top, left+width, top+height))){
                        str_text_box += text_box_texts[RectF(left, top, left+width, top+height)]
                    }
                    if(!word.getString("WordText").toString().isNullOrEmpty()) {
                        text_box_texts[RectF(left, top, left + width, top + height)] =
                            str_text_box + "," + word.getString("WordText")
                                .toString(); //append to text box texts
                    }
                    Log.d("For text_box: ${left} and ${top} and ${left+width} and ${top+height} -> ", word_text)
                }
            }
            Log.d("The Parsed Text is: ", parse_res_obbj.getString("ParsedText"))
            //call the image attach function
            recognized_image_spawn()
        }
        catch (e:Exception){
            Log.d("Exception is: ", e.toString())
            Toast.makeText(this, "${e.toString()}", Toast.LENGTH_SHORT).show()
        }

    }

    private fun compare(text:String, prod:String) : Int{
        if (prod.contains(text)){
            return 1
        }
        else{
            return 0
        }
    }

    private fun compare_in_box(prod:String, box_str:String) : Int{

        var mod_prod = prod.lowercase().split(" "); //only convert to lower case and keep string and split into word
        Log.d("The product names is: $mod_prod", " ")
        var counter = 0; //increment it whenever found
        for (word in mod_prod){ //for each word
            //if word contains prod or prod contains word
            Log.d("BOX_STR: ${box_str} and word: $word","<-")
            if (box_str.contains(word)){
                counter += 1;
            }
        }
        if(counter == mod_prod.size){
            Log.d("I came here mom", " and")
            return 1
        }
        Log.d("I failed mom", " and")
        return 0;
    }

    private fun speak_string(word:String){
        tts = TextToSpeech(this, this);
        tts!!.speak(word, TextToSpeech.QUEUE_FLUSH, null, "");
        tts!!.stop()
    }

    private fun word_for_search_enhancer(word:String):String{
        var mod_word = word.replace("\n", "")
        mod_word = mod_word.replace(" ", "")
        mod_word = mod_word.replace(".", "")
        mod_word = mod_word.lowercase()
        return mod_word;
    }

    private fun SpawnButtons(left:Float, top:Float, height:Float, width:Float, found:Int){ //if FOUND IS 2, means we have the rectangle for testing
        //PAINT VARS FOR CANVAS
        mypaint.style = Paint.Style.STROKE
        if(found == 0) { //IF NOT FOUND
            mypaint.strokeWidth = 3F
            mypaint.setColor(Color.BLUE)
        }
        else if(found == 1 ){ //IF TEXT FOUND
            mypaint.strokeWidth = 4F
            mypaint.setColor(Color.RED) //IF FOUND
        }
        else if(found == 2){ //FOR RECTANGLES NOT CONTAINING TEXT
            mypaint.strokeWidth = 6F
            mypaint.setColor(Color.CYAN) //IF FOUND
        }
        else if(found == 3){ //RECTANGLES CONTAIN TEXT OF PROD
            mypaint.strokeWidth = 11F
            mypaint.setColor(Color.MAGENTA) //IF FOUND
        }
        tmpbmp = current_img_bitmap?.let { Canvas(it) };
        if (tmpbmp != null) {
//            //var rect = RectF(left, top, left+width, top+height); //send the parameters here
//            //SWAPPED FOR 90 DEGREE ROTATION
//            val __width = current_img_bitmap!!.width!!.toFloat()
//            val _left:Float = ((__width - (top + height))).toFloat()
//            val _top:Float = left
//            val _height:Float = _top + width;
//            val _width:Float = _left + height
//            Log.d("The old lengths for left, top, height and width : ", left.toString() + " "
//            + top.toString() + " " + height.toString() + " " + width.toString())
//            Log.d("The new lengths with bitmap width $__width} are: ", _left.toString() + " "
//                    + _top.toString() + " " + _height.toString() + " " + _width.toString())
//            var rect = RectF(_left, _top, _width, _height)
            var rect=RectF(left, top, left+width, top+height)

            tmpbmp!!.drawRect(rect, mypaint)
        }
    }

    private fun recognized_image_spawn(){
        for (elem in rect_arr!!){
            Log.d("The elements are: ", elem.toString()) //the rectangles are given with coordinates here and now need to draw
            object_box_text_fill(elem.left * current_img_bitmap!!.width, elem.top * current_img_bitmap!!.height, elem.height() * current_img_bitmap!!.height, elem.width() * current_img_bitmap!!.width)
            //now all we need to do is find if the product name is found in the matched object boundary boxes
            if (compare_in_box(prod_name!!, word_for_search_enhancer(object_texts[RectF(
            elem.left * current_img_bitmap!!.width,
            elem.top * current_img_bitmap!!.height,
            elem.left * current_img_bitmap!!.width + elem.width() * current_img_bitmap!!.width,
            elem.top * current_img_bitmap!!.height + elem.height() * current_img_bitmap!!.height
            )]!!)) == 1){
                SpawnButtons(elem.left * current_img_bitmap!!.width,
                    elem.top * current_img_bitmap!!.height,
                    elem.height() * current_img_bitmap!!.height,
                    elem.width() * current_img_bitmap!!.width , 3) //IF FOUND
            }
            else{
                SpawnButtons(elem.left * current_img_bitmap!!.width,
                    elem.top * current_img_bitmap!!.height,
                    elem.height() * current_img_bitmap!!.height,
                    elem.width() * current_img_bitmap!!.width , 2) //IF NOT FOUND
            }
        }
        for (_object in object_texts.keys) {
            Log.d("The text is: ", object_texts[_object]!!.toString().replace(" ", ""));
            //we also want to find that to this bounding box if textboxes are attached or not
            button_spawner_on_object(_object.left,
                _object.top,
                _object.width(),
                _object.height(),
                object_texts[_object]!!.toString().replace(" ", "")
                )
        }
        var im_view = findViewById<ImageView>(R.id.imageView)
        im_view.setImageDrawable(BitmapDrawable(resources, current_img_bitmap))
        Log.d("Rect spawned", "A")
        Log.d("Rotation is: ", rotation.toString())

    }

    private fun button_spawner_on_object(left:Float, top:Float, width:Float, height:Float, cont_str:String){
        //find the imageview margin from parent constraint layout
        //add this to the top
        var im_view = findViewById<ImageView>(R.id.imageView)
        im_view.requestLayout()
        val layout_param = im_view.layoutParams as RelativeLayout.LayoutParams;
        Log.d("The layout params are: ", layout_param.topMargin.toString())


        //create button
        var layout_params = RelativeLayout.LayoutParams(((width*1200)/800).toInt(),
            ((height*1200)/700).toInt());
        Log.d("The left is: ${left} and top is: ${top} and width is: ${width} and height is: ${height}..", ",")
        layout_params.setMargins(((left*1200)/800).toInt(), ((top*1200)/700).toInt(), 0, 0)
        var Button = MaterialButton(this)
        Button.layoutParams = layout_params;
        Log.d("THE BUTTON MARGINS ARE: ", Button.marginTop.toString())
        Button.setBackgroundColor(Color.TRANSPARENT)
        Button.strokeColor = android.content.res.ColorStateList.valueOf(Color.BLUE)
        Button.strokeWidth = 5
        Button.setText("OBJ")
        Button.textSize = 26F
        Button.setTypeface(Typeface.DEFAULT_BOLD, 1)
        Button.setTextColor(Color.WHITE)
        Button.setOnClickListener{
            //Toast.makeText(this, "$cont_str", Toast.LENGTH_SHORT).show()
            //add a text view to the layout of a specific height and width
            if(text_view!!.visibility == View.INVISIBLE){
                text_view!!.visibility = View.VISIBLE;
            }
           if(detect_text!!.visibility == View.INVISIBLE){
                detect_text!!.visibility = View.VISIBLE;
            }
            //lets add the rest of the code here
            var mod_details = cont_str.replace(" ", "")
            var sep_words = mod_details.split(",")
            var final_str = ""
            for (word in sep_words){
                if(word.isNotEmpty() && word.length > 2) {
                    final_str += word + "\n";
                    Log.d("THE WORD IS: ", word)
                }
            }
            text_view!!.text = final_str;
        }

        //add to button hashmap
        dynamic_buttons[Button] = cont_str;

        //add button the constraint layout
        layout!!.addView(Button);
    }

    private fun object_box_text_fill(left:Float, top:Float, height:Float, width:Float){
        val _left = left;
        val _top = top;
        val _bottom = top+height;
        val _right = left+width;

        for (text_box in text_box_texts.keys){
            Log.d("Text box is: ${text_box.left} and ${text_box.top} and ${text_box.bottom} and ${text_box.right}", text_box_texts[text_box]!!.toString())
            Log.d("The object box is: ${_left} and ${_top} and ${_bottom} and ${_right}", " .")
            if(text_box.left >= _left && text_box.top >= _top && text_box.right <= _right && text_box.bottom <= text_box.bottom){
                var str = " "
                if(object_texts.containsKey(RectF(_left, _top, _right, _bottom))){
                    str += object_texts[RectF(_left, _top, _right, _bottom)];
                }

                object_texts[RectF(_left, _top, _right, _bottom)] = str + " " + text_box_texts[text_box]!!;
                Log.d("bc -> ", object_texts[RectF(_left, _top, _right, _bottom)].toString())
            }
        }

    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // set US English as language for tts
            val result = tts!!.setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.d("TTS","The Language specified is not supported!")
            } else {
                btnspeak!!.isEnabled=true;
            }

        } else {
            Log.d("TTS", "Initilization Failed!")
        }
    }

}