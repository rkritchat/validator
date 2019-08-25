package com.rkritchat.validator;

import com.rkritchat.model.NoteBookModel;
import com.rkritchat.model.ProductModel;
import com.rkritchat.model.UserModel;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 *  This validator still have weakness because if some one changing getterMethod name system will away throw RuntimeException
 *  so it will hard to change and hard for understand text pattern for someone new with this validator
 */
public class ObjectValidator {

    /**
     *
     * This method use for validate object inside object to avoid legacy checking
     * EX
     *     On text "userModel.getProductModel().getNoteBookModel()->.getName().getPrice().getTest()" [Arrow style]
     *
     *          if(userModel!=null){
     *              ProductModel productModel = userModel.getProductModel();
     *              if(productModel != null){
     *                  NoteBookModel noteBookModel = userModel.getProductModel().getNoteBookModel();
     *                  if(noteBookModel!=null){
     *                      if(noteBookModel.getName() == null){
     *                          return "Name is required";
     *                      }
     *                      if(noteBookModel.getPrice()){
     *                          return "Price is required";
     *                      }
     *                      if(noteBookModel.getTest()){
     *                          return "Test is required";
     *                      }
     *                  }
     *              }
     *          }
     *
     * The validator allow two method style
     * I. checking object inside object [normal style]
     *    - Ex
     *          INPUT : userModel.getProductModel().getNoteBookModel().getName()
     *          PROCESS :
     *                   1. check getProductModel object if null return Product
     *                   2. check getNoteBookModel that belong to ProductModel object if null return NoteBook
     *                   3. check getName that belong to NoteBookModel object if null return Name
     *                   ** if all method not null then return null
     *
     * II. checking object inside object and all method of last object [arrow style]
     *    - Ex
     *          INPUT : userModel.getProductModel().getNoteBookModel()->.getName().getPrice().getTest()
     *          PROCESS :
     *                   1. split text from arrow format to normal format result will be list of String
     *                      - userModel.getProductModel().getNoteBookModel().getName()
     *                      - userModel.getProductModel().getNoteBookModel().getPrice()
     *                      - userModel.getProductModel().getNoteBookModel().getTest()
     *                   2. process on normal style
     *                  ** PS if using this style system will store catch for avoid to getting getter method
     *                     In case mainObject is null will return mainObject name
     */
    public static String validateObject(Object mainObject, String text) {
        if(mainObject == null){
            //return userModelClass.getName().replace("com.rkritchat.model.","").replace("Model","");
            throw new NullPointerException("Main object is required.");
        }
        List<String> method = initMethod(text);
        Map<String, Object> storage = new HashMap<>();
        for (String target : method) {
            System.out.println("validate method " + target);
            List<String> methods = removeBrackets(target);
            String result = execute(mainObject, methods, storage);
            if(result != null){
                return result;
            }
        }
        return null;
    }

    /**
     *   Checking style
     *   this method arrow two style
     *
     *   I.  Normal style
     *      - EX userModel.getProductModel().getNoteBookModel().getName()
     *
     *   II. Arrow style
     *      - EX userModel.getProductModel().getNoteBookModel()->.getName().getPrice().getTest()
     *
     */
    private static List<String> initMethod(String text) {
        if (text.contains("->")) {
            String[] str = text.split("->");
            String[] split = str[1].split("\\.");
            return Stream.of(split).filter(e -> !e.equals("")).map(e -> str[0] + "." + e).collect(Collectors.toList());
        } else {
            return Collections.singletonList(text);
        }
    }

    private static String execute(Object object, List<String> methods, Map<String, Object> storage){
        return execute(object, methods, 0, storage);
    }

    /**
     *
     *  This is recursive method that use for calling searchGetterMethod
     *  This method support catch and will exist when all method is validated.
     */
    private static String execute(Object object, List<String> methods, int counter, Map<String, Object> storage) {
        Object cache = storage.get(methods.get(counter));
        Object rp = cache !=null ? cache : searchGetterMethod(object, methods.get(counter));
        if (test(rp)) {
            return methods.get(counter).replace("get", "").replace("Model", "");
        } else {
            if (cache == null) storage.put(methods.get(counter), rp);
            return methods.size() == counter + 1 ? null : execute(rp, methods, ++counter, storage);
        }
    }

    /**
     *  This method for get object by using reflection, the method will searching method name by target
     *  if found then return result of invoking <getter> method
     *
     */
    private static Object searchGetterMethod(Object object, String target) {
        try {
            if(target == null || !target.contains("get")) return null;
            Method[] declaredMethods = object.getClass().getDeclaredMethods();
            for (Method method : declaredMethods) {
                if(target.equals(method.getName())){
                    return method.invoke(object);
                }
            }
        } catch (Exception err) {
            System.out.println("Exception occurred" + err);
        }
        throw new RuntimeException("Method " + target+ " not found on " + object);
    }

    /**
     *
     *  This method use for remove Brackets and split the getter method
     */
    private static List<String> removeBrackets(String text) {
        String[] split = text.split("\\.");
        return Stream.of(split).filter(e -> e.contains("()")).map(e -> e.replace("()", "")).collect(Collectors.toList());
    }

    private boolean validateString(String text){
        return Optional.ofNullable(text).map(String::trim).filter(e -> e.length() > 0).isPresent();
    }

    private boolean validateList(List<?> test) {
        return Optional.ofNullable(test).map(List::size).orElse(0) > 0;
    }

    private boolean isContainString(String text) {
        return Optional.ofNullable(text).filter(e -> e.contains(" ")).isPresent();
    }

    private void isNumeric(String text, String message){
       // Optional.ofNullable(text).filter(StringUtils::isNumeric).orElseThrow(() -> new RuntimeException(message));
    }

    private static boolean test(Object obj){
        return obj == null ||
                (obj instanceof String && ((String) obj).trim().equals("")) ||
                (obj instanceof Number && ((Number) obj).longValue() == 0)  ||
                (obj instanceof List && ((List) obj).size() == 0)           ||
                (obj.getClass().isArray() && testArray(obj));
    }

    private static boolean testArray(Object obj){
        return (obj instanceof Byte[] && ((Byte[]) obj).length == 0) ||
                (obj instanceof Short[] && ((Short[]) obj).length == 0) ||
                (obj instanceof Integer[] && ((Integer[]) obj).length == 0) ||
                (obj instanceof Long[] && ((Long[]) obj).length == 0) ||
                (obj instanceof Float[] && ((Float[]) obj).length == 0) ||
                (obj instanceof Double[] && ((Double[]) obj).length == 0) ||
                (obj instanceof Character[] && ((Character[]) obj).length == 0) ||
                (obj instanceof Boolean[] && ((Boolean[]) obj).length == 0) ||
                (obj instanceof Object[] && ((Object[]) obj).length == 0);
    }
}
