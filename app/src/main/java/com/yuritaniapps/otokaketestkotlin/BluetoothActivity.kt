package com.yuritaniapps.otokaketestkotlin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.FragmentTransaction

class BluetoothActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        val firstBluetoothFragment = BluetoothFragment("ESP32test")
        val firstTransaction = supportFragmentManager.beginTransaction()
        firstTransaction.add(R.id.fragmentContainer, firstBluetoothFragment)
        firstTransaction.commit()

        val secondBluetoothFragment = BluetoothFragment("RNBT-85CD")
        val secondTransaction = supportFragmentManager.beginTransaction()
        secondTransaction.add(R.id.fragmentContainer, secondBluetoothFragment)
        secondTransaction.commit()
    }
}