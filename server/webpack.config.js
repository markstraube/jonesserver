var path = require('path');

module.exports = {
	entry: './src/main/js/app.js',
	mode: 'development',
	output: {
		path: __dirname,
		filename: './src/main/resources/static/built/bundle.js'
	},
	resolve: {
		alias: {
			react: path.resolve('./node_modules/react'),
		},
	},
	module: {
		rules: [
			{
				test: /\.ts$/,
				use: [
					{ loader: 'awesome-typescript-loader' }, { loader: 'angular2-template-loader?keepUrl=true' }],
			},
			{
				test: /\.(js|jsx)$/,
				use: 'babel-loader',
			},
			{
				test: /\.css$/,
				use: [
					{
						loader: 'css-loader',
						options: {
							modules: true
						}
					},
				]
			},
			{
				test: /\.s[ac]ss$/i,
				use: [
					// Creates `style` nodes from JS strings
					"style-loader",
					// Translates CSS into CommonJS
					"css-loader",
					// Compiles Sass to CSS
					{
						loader: "sass-loader", options: {
							// Prefer `dart-sass`
							implementation: require("sass"),
						},
					},
				],
			}
		]
	}
};