import { defineStore } from "pinia";
import {ref} from 'vue'

export const useUserTokenStore = defineStore('userToken',()=>{
    const tokenName = ref('')
    const tokenValue = ref('')

    function setToken(name,value){
        tokenName.value=name,
        tokenValue.value=value
    }
    function removeToken(){
        tokenName.value='',
        tokenValue.value=''
    }
    return{
        tokenName,
        tokenValue,
        setToken,
        removeToken,
    }
},{
    persist:true
})

